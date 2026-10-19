#!/usr/bin/env bash
set -euo pipefail

LOAD_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(git -C "$LOAD_DIR" rev-parse --show-toplevel)"
PURCHASE_PATH="${1:?구매 경로 db 또는 redis를 입력하세요}"
case "$PURCHASE_PATH" in
  db|redis) ;;
  *) printf '구매 경로는 db 또는 redis여야 합니다\n' >&2; exit 2 ;;
esac
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)-$(openssl rand -hex 8)"
WORK_DIR="$ROOT_DIR/load/build/purchase-$PURCHASE_PATH-$RUN_ID"
RESULTS_DIR="$ROOT_DIR/load/k6/results"
SUMMARY_FILE="$RESULTS_DIR/purchase-$PURCHASE_PATH-$RUN_ID.json"
RECONCILIATION_FILE="$RESULTS_DIR/reconcile-$PURCHASE_PATH-$RUN_ID.json"
ENVIRONMENT_FILE="$RESULTS_DIR/environment-$PURCHASE_PATH-$RUN_ID.txt"

POSTGRES_IMAGE="postgres:16-alpine"
REDIS_IMAGE="redis:7.4-alpine"
JWKS_IMAGE="nginx:1.27.5-alpine"
K6_IMAGE="grafana/k6:1.3.0"
POSTGRES_CONTAINER="dropgate-load-postgres-$RUN_ID"
REDIS_CONTAINER="dropgate-load-redis-$RUN_ID"
JWKS_CONTAINER="dropgate-load-jwks-$RUN_ID"
K6_CONTAINER="dropgate-load-k6-$RUN_ID"
POSTGRES_PORT=15432
REDIS_PORT=16379
JWKS_PORT=18089
ORDER_PORT=8082
DATABASE_NAME="dropgate"

REQUEST_COUNT=10000
TOTAL_QUANTITY=100
OPEN_DELAY_SECONDS=3
CLOSE_AFTER_SECONDS=3600
WAIT_ATTEMPTS=60
OUTBOX_WAIT_ATTEMPTS=60
HEALTH_TIMEOUT_SECONDS=2
STOP_WAIT_ATTEMPTS=10

ORDER_URL="http://localhost:$ORDER_PORT"
HEALTH_URL="$ORDER_URL/load-$RUN_ID/health"
ORDER_URL_FROM_DOCKER="http://host.docker.internal:$ORDER_PORT"
ORDER_DB_PASSWORD="$(openssl rand -hex 16)"
POSTGRES_SUPERUSER_PASSWORD="$(openssl rand -hex 16)"
ORDER_PID=""
JAVA_BIN=""
ORDER_JAR=""

log() {
  printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*"
}

cleanup() {
  local exit_code=$?
  if [[ "$exit_code" -ne 0 && -f "$WORK_DIR/order-service.log" ]]; then
    log "실패했습니다. order-service 로그 끝부분입니다"
    tail -n 40 "$WORK_DIR/order-service.log"
  fi
  if [[ -n "$ORDER_PID" ]] && kill -0 "$ORDER_PID" 2>/dev/null; then
    kill "$ORDER_PID" 2>/dev/null || true
    for _ in $(seq "$STOP_WAIT_ATTEMPTS"); do
      if ! kill -0 "$ORDER_PID" 2>/dev/null; then break; fi
      sleep 1
    done
    if kill -0 "$ORDER_PID" 2>/dev/null; then kill -KILL "$ORDER_PID" 2>/dev/null || true; fi
    wait "$ORDER_PID" 2>/dev/null || true
  fi
  local container owner
  for container in "$K6_CONTAINER" "$JWKS_CONTAINER" "$POSTGRES_CONTAINER" "$REDIS_CONTAINER"; do
    owner="$(docker inspect --format '{{index .Config.Labels "dropgate.load.run"}}' "$container" 2>/dev/null || true)"
    if [[ "$owner" == "$RUN_ID" ]]; then
      docker rm -fv "$container" >/dev/null 2>&1 || true
    fi
  done
  log "컨테이너와 order-service 프로세스를 정리했습니다"
}

wait_until() {
  local description="$1"
  shift
  for _ in $(seq "$WAIT_ATTEMPTS"); do
    if "$@" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  log "$description 을 ${WAIT_ATTEMPTS}초 동안 기다렸지만 준비되지 않았습니다"
  return 1
}

find_order_service_jar() {
  local candidate
  local jars=()
  for candidate in "$ROOT_DIR"/order-service/build/libs/order-service-*.jar; do
    if [[ -f "$candidate" && "$candidate" != *-plain.jar ]]; then
      jars+=("$candidate")
    fi
  done
  if [[ "${#jars[@]}" -ne 1 ]]; then
    printf '실행할 order-service jar는 정확히 하나여야 합니다: %s개\n' "${#jars[@]}" >&2
    return 1
  fi
  printf '%s\n' "${jars[0]}"
}

find_java_21() {
  local selected_home executable version
  if [[ -n "${JAVA_HOME:-}" ]]; then
    executable="$JAVA_HOME/bin/java"
  elif [[ -x /usr/libexec/java_home ]]; then
    selected_home="$(/usr/libexec/java_home -v 21)" || return 1
    executable="$selected_home/bin/java"
  else
    executable="$(command -v java)" || return 1
  fi
  version="$("$executable" -version 2>&1)" || return 1
  if [[ ! "$version" =~ version[[:space:]]+\"21[.\"] ]]; then
    printf 'JDK 21이 필요합니다: %s\n' "$version" >&2
    return 1
  fi
  printf '%s\n' "$executable"
}

build_order_service() {
  log "order-service jar를 빌드합니다"
  JAVA_HOME="$(dirname "$(dirname "$JAVA_BIN")")" "$ROOT_DIR/gradlew" -p "$ROOT_DIR" :order-service:bootJar --quiet
}

issue_tokens() {
  log "측정용 RSA 키로 구매 요청 ${REQUEST_COUNT}건의 토큰과 ADMIN 토큰을 만듭니다"
  node "$ROOT_DIR/load/token/issue-tokens.mjs" "$WORK_DIR" "$REQUEST_COUNT"
}

run_psql() {
  docker exec -i "$POSTGRES_CONTAINER" psql -h 127.0.0.1 -U postgres -d "$DATABASE_NAME" "$@"
}

start_postgres() {
  log "PostgreSQL 16을 띄우고 orders 스키마와 order_user를 만듭니다"
  docker run -d --name "$POSTGRES_CONTAINER" \
    --label "dropgate.load.run=$RUN_ID" \
    -e POSTGRES_PASSWORD="$POSTGRES_SUPERUSER_PASSWORD" -e POSTGRES_DB="$DATABASE_NAME" \
    -p "$POSTGRES_PORT:5432" "$POSTGRES_IMAGE" >/dev/null
  wait_until "PostgreSQL 접속" run_psql -c "SELECT 1"
  run_psql -v ON_ERROR_STOP=1 -q \
    -c "CREATE ROLE order_user LOGIN PASSWORD '$ORDER_DB_PASSWORD'" \
    -c "CREATE SCHEMA orders AUTHORIZATION order_user"
}

start_redis() {
  log "Redis를 띄웁니다"
  docker run -d --name "$REDIS_CONTAINER" --label "dropgate.load.run=$RUN_ID" \
    -p "$REDIS_PORT:6379" "$REDIS_IMAGE" >/dev/null
  wait_until "Redis 접속" docker exec "$REDIS_CONTAINER" redis-cli PING
}

start_jwks_server() {
  log "정적 JWKS 서버를 띄웁니다"
  docker run -d --name "$JWKS_CONTAINER" --label "dropgate.load.run=$RUN_ID" \
    -v "$WORK_DIR/jwks.json:/usr/share/nginx/html/jwks.json:ro" \
    -p "$JWKS_PORT:80" "$JWKS_IMAGE" >/dev/null
  wait_until "JWKS 서버" curl -sf "http://localhost:$JWKS_PORT/jwks.json"
}

start_order_service() {
  log "order-service를 서비스 1개로 띄웁니다"
  DROPGATE_PURCHASE_PATH="$PURCHASE_PATH" \
    DROPGATE_QUEUE_ENABLED=false \
    DROPGATE_REDIS_HOST=localhost \
    DROPGATE_REDIS_PORT="$REDIS_PORT" \
    DROPGATE_DB_URL="jdbc:postgresql://localhost:$POSTGRES_PORT/$DATABASE_NAME" \
    DROPGATE_DB_ORDER_PASSWORD="$ORDER_DB_PASSWORD" \
    DROPGATE_AUTH_JWKS_URI="http://localhost:$JWKS_PORT/jwks.json" \
    "$JAVA_BIN" -jar "$ORDER_JAR" --server.port="$ORDER_PORT" \
    --management.endpoints.web.base-path="/load-$RUN_ID" \
    >"$WORK_DIR/order-service.log" 2>&1 &
  ORDER_PID=$!
  wait_until "order-service" is_order_service_ready
}

is_order_service_ready() {
  kill -0 "$ORDER_PID" 2>/dev/null && curl --max-time "$HEALTH_TIMEOUT_SECONDS" -sf "$HEALTH_URL" \
    -H "Authorization: Bearer $(cat "$WORK_DIR/admin-token.txt")"
}

create_drop() {
  local open_at close_at
  open_at="$(jq -nr "now + $OPEN_DELAY_SECONDS | todate")"
  close_at="$(jq -nr "now + $CLOSE_AFTER_SECONDS | todate")"
  curl -sf -X POST "$ORDER_URL/api/admin/drops" \
    -H "Authorization: Bearer $(cat "$WORK_DIR/admin-token.txt")" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"purchase-$PURCHASE_PATH-$RUN_ID\",\"totalQuantity\":$TOTAL_QUANTITY,\"openAt\":\"$open_at\",\"closeAt\":\"$close_at\"}" |
    jq -r '.id'
}

is_drop_open() {
  [[ "$(curl -sf "$ORDER_URL/api/drops/$1" | jq -r '.status')" == "OPEN" ]] &&
    [[ "$(docker exec "$REDIS_CONTAINER" redis-cli --raw GET "drop:$1:stock")" == "$TOTAL_QUANTITY" ]]
}

run_k6() {
  local drop_id="$1"
  log "k6 가상 사용자 500으로 구매 요청 ${REQUEST_COUNT}건을 보냅니다"
  docker run --rm --name "$K6_CONTAINER" --label "dropgate.load.run=$RUN_ID" \
    --add-host=host.docker.internal:host-gateway \
    -v "$ROOT_DIR/load/k6:/scripts:ro" -v "$WORK_DIR:/data:ro" -v "$RESULTS_DIR:/results" \
    "$K6_IMAGE" run --quiet \
    -e DROPGATE_LOAD_BASE_URL="$ORDER_URL_FROM_DOCKER" \
    -e DROPGATE_LOAD_DROP_ID="$drop_id" \
    -e DROPGATE_LOAD_REQUEST_TOKENS_FILE=/data/purchase-request-tokens.json \
    --summary-export "/results/$(basename "$SUMMARY_FILE")" \
    /scripts/purchase.js
}

wait_for_outbox() {
  for _ in $(seq "$OUTBOX_WAIT_ATTEMPTS"); do
    if [[ "$(run_psql -tAc "SELECT count(*) FROM orders.outbox WHERE status = 'PENDING'")" == "0" ]]; then
      return 0
    fi
    sleep 1
  done
  log "outbox PENDING이 ${OUTBOX_WAIT_ATTEMPTS}초 안에 비워지지 않았습니다"
  return 1
}

print_accuracy_report() {
  local drop_id="$1"
  log "$PURCHASE_PATH 경로의 재고와 주문을 대조합니다"
  DROPGATE_LOAD_POSTGRES_CONTAINER="$POSTGRES_CONTAINER" \
    DROPGATE_LOAD_REDIS_CONTAINER="$REDIS_CONTAINER" \
    DROPGATE_LOAD_DATABASE_NAME="$DATABASE_NAME" \
    "$LOAD_DIR/reconcile.sh" "$drop_id" "$PURCHASE_PATH" | tee "$RECONCILIATION_FILE"
}

print_environment() {
  local docker_version redis_version postgres_version java_version
  docker_version="$(docker version --format '{{.Server.Platform.Name}}, Engine {{.Server.Version}}')"
  redis_version="$(docker exec "$REDIS_CONTAINER" redis-server --version)"
  postgres_version="$(run_psql -tAc 'SHOW server_version')"
  java_version="$("$JAVA_BIN" -version 2>&1)"
  log "$docker_version"
  log "$redis_version"
  log "PostgreSQL $postgres_version, $java_version, $K6_IMAGE"
}

print_summary() {
  log "k6 요약: $SUMMARY_FILE"
  jq '{
    requests: .metrics.http_reqs.count,
    confirmed: (.metrics.purchase_confirmed.count // 0),
    soldOut: (.metrics.purchase_sold_out.count // 0),
    alreadyPurchased: (.metrics.purchase_already_purchased.count // 0),
    unexpected: (.metrics.purchase_unexpected.count // 0),
    durationMillis: (.metrics.http_req_duration | {avg, med, "p(95)", "p(99)", max}),
    confirmedDurationMillis: (.metrics["http_req_duration{status:201}"] | {med, "p(95)", "p(99)", max}),
    rejectedDurationMillis: (.metrics["http_req_duration{status:409}"] | {med, "p(95)", "p(99)", max}),
    requestsPerSecond: .metrics.http_reqs.rate
  }' "$SUMMARY_FILE"
}

main() {
  trap cleanup EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
  mkdir -p "$WORK_DIR" "$RESULTS_DIR"
  JAVA_BIN="$(find_java_21)"
  build_order_service
  ORDER_JAR="$(find_order_service_jar)"
  issue_tokens
  start_postgres
  start_redis
  start_jwks_server
  start_order_service
  local drop_id
  drop_id="$(create_drop)"
  log "드롭 $drop_id 를 등록했고 스케줄러가 OPEN으로 바꾸기를 기다립니다"
  wait_until "드롭 OPEN 전이" is_drop_open "$drop_id"
  kill -0 "$ORDER_PID"
  run_k6 "$drop_id"
  kill -0 "$ORDER_PID"
  wait_for_outbox
  print_accuracy_report "$drop_id"
  jq -e --argjson expected "$TOTAL_QUANTITY" '.confirmed == $expected and .canceled == 0 and
    .status == "SOLD_OUT" and .outbox.pending == 0 and .outbox.failed == 0 and .outbox.published == $expected and
    .streamLength == $expected' "$RECONCILIATION_FILE" >/dev/null
  jq -e --argjson expected "$REQUEST_COUNT" --argjson quantity "$TOTAL_QUANTITY" '
    .metrics.http_reqs.count == $expected and .metrics.purchase_confirmed.count == $quantity and
    (.metrics.purchase_unexpected.count // 0) == 0 and
    ((.metrics.purchase_confirmed.count // 0) + (.metrics.purchase_sold_out.count // 0) +
      (.metrics.purchase_already_purchased.count // 0)) == $expected' "$SUMMARY_FILE" >/dev/null
  print_summary
  print_environment | tee "$ENVIRONMENT_FILE"
}

main "$@"
