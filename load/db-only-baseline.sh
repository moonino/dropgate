#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WORK_DIR="$ROOT_DIR/load/build/db-only-baseline"
RESULTS_DIR="$ROOT_DIR/load/k6/results"
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)"
SUMMARY_FILE="$RESULTS_DIR/db-only-baseline-$RUN_ID.json"

POSTGRES_IMAGE="postgres:16-alpine"
JWKS_IMAGE="nginx:1.27.5-alpine"
K6_IMAGE="grafana/k6:1.3.0"
POSTGRES_CONTAINER="dropgate-load-postgres"
JWKS_CONTAINER="dropgate-load-jwks"
K6_CONTAINER="dropgate-load-k6"
POSTGRES_PORT=15432
JWKS_PORT=18089
ORDER_PORT=8082
DATABASE_NAME="dropgate"

REQUEST_COUNT=10000
TOTAL_QUANTITY=100
OPEN_DELAY_SECONDS=3
CLOSE_AFTER_SECONDS=3600
WAIT_ATTEMPTS=60

ORDER_URL="http://localhost:$ORDER_PORT"
ORDER_URL_FROM_DOCKER="http://host.docker.internal:$ORDER_PORT"
ORDER_DB_PASSWORD="$(openssl rand -hex 16)"
ORDER_PID=""

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
    kill "$ORDER_PID"
    wait "$ORDER_PID" 2>/dev/null || true
  fi
  docker rm -f "$K6_CONTAINER" "$JWKS_CONTAINER" "$POSTGRES_CONTAINER" >/dev/null 2>&1 || true
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
  find "$ROOT_DIR/order-service/build/libs" -name 'order-service-*.jar' ! -name '*-plain.jar' | head -n 1
}

find_java_21() {
  if [[ -x /usr/libexec/java_home ]]; then
    echo "$(/usr/libexec/java_home -v 21)/bin/java"
  else
    echo "java"
  fi
}

build_order_service() {
  log "order-service jar를 빌드합니다"
  "$ROOT_DIR/gradlew" -p "$ROOT_DIR" :order-service:bootJar --quiet
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
  docker run -d --name "$POSTGRES_CONTAINER" -e POSTGRES_PASSWORD="$ORDER_DB_PASSWORD" -e POSTGRES_DB="$DATABASE_NAME" \
    -p "$POSTGRES_PORT:5432" "$POSTGRES_IMAGE" >/dev/null
  wait_until "PostgreSQL 접속" run_psql -c "SELECT 1"
  run_psql -v ON_ERROR_STOP=1 -q \
    -c "CREATE ROLE order_user LOGIN PASSWORD '$ORDER_DB_PASSWORD'" \
    -c "CREATE SCHEMA orders AUTHORIZATION order_user"
}

start_jwks_server() {
  log "정적 JWKS 서버를 띄웁니다"
  docker run -d --name "$JWKS_CONTAINER" -v "$WORK_DIR/jwks.json:/usr/share/nginx/html/jwks.json:ro" \
    -p "$JWKS_PORT:80" "$JWKS_IMAGE" >/dev/null
  wait_until "JWKS 서버" curl -sf "http://localhost:$JWKS_PORT/jwks.json"
}

start_order_service() {
  log "order-service를 서비스 1개로 띄웁니다"
  DROPGATE_DB_URL="jdbc:postgresql://localhost:$POSTGRES_PORT/$DATABASE_NAME" \
    DROPGATE_DB_ORDER_PASSWORD="$ORDER_DB_PASSWORD" \
    DROPGATE_AUTH_JWKS_URI="http://localhost:$JWKS_PORT/jwks.json" \
    "$(find_java_21)" -jar "$(find_order_service_jar)" --server.port="$ORDER_PORT" \
    >"$WORK_DIR/order-service.log" 2>&1 &
  ORDER_PID=$!
  wait_until "order-service" curl -sf "$ORDER_URL/actuator/health"
}

create_drop() {
  local open_at close_at
  open_at="$(jq -nr "now + $OPEN_DELAY_SECONDS | todate")"
  close_at="$(jq -nr "now + $CLOSE_AFTER_SECONDS | todate")"
  curl -sf -X POST "$ORDER_URL/api/admin/drops" \
    -H "Authorization: Bearer $(cat "$WORK_DIR/admin-token.txt")" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"db-only-baseline-$RUN_ID\",\"totalQuantity\":$TOTAL_QUANTITY,\"openAt\":\"$open_at\",\"closeAt\":\"$close_at\"}" |
    jq -r '.id'
}

is_drop_open() {
  [[ "$(curl -sf "$ORDER_URL/api/drops/$1" | jq -r '.status')" == "OPEN" ]]
}

run_k6() {
  local drop_id="$1"
  log "k6 가상 사용자 500으로 구매 요청 ${REQUEST_COUNT}건을 보냅니다"
  docker run --rm --name "$K6_CONTAINER" --add-host=host.docker.internal:host-gateway \
    -v "$ROOT_DIR/load/k6:/scripts:ro" -v "$WORK_DIR:/data:ro" -v "$RESULTS_DIR:/results" \
    "$K6_IMAGE" run --quiet \
    -e DROPGATE_LOAD_BASE_URL="$ORDER_URL_FROM_DOCKER" \
    -e DROPGATE_LOAD_DROP_ID="$drop_id" \
    -e DROPGATE_LOAD_REQUEST_TOKENS_FILE=/data/purchase-request-tokens.json \
    --summary-export "/results/$(basename "$SUMMARY_FILE")" \
    /scripts/purchase.js
}

verify_accuracy() {
  local drop_id="$1"
  log "DB에서 초과, 중복, 재고 차를 대조합니다"
  run_psql -v ON_ERROR_STOP=1 -v drop_id="$drop_id" -P pager=off <"$ROOT_DIR/load/sql/verify-purchase-accuracy.sql"
}

print_environment() {
  log "$(docker version --format '{{.Server.Platform.Name}}, Engine {{.Server.Version}}')"
  log "PostgreSQL $(run_psql -tAc 'SHOW server_version'), $("$(find_java_21)" -version 2>&1 | head -n 1), $K6_IMAGE"
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
  mkdir -p "$WORK_DIR" "$RESULTS_DIR"
  build_order_service
  issue_tokens
  start_postgres
  start_jwks_server
  start_order_service
  local drop_id
  drop_id="$(create_drop)"
  log "드롭 $drop_id 를 등록했고 스케줄러가 OPEN으로 바꾸기를 기다립니다"
  wait_until "드롭 OPEN 전이" is_drop_open "$drop_id"
  run_k6 "$drop_id"
  print_summary
  verify_accuracy "$drop_id"
  print_environment
}

main "$@"
