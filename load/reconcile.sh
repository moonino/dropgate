#!/usr/bin/env bash
set -euo pipefail

LOAD_DIR="$(cd "$(dirname "$0")" && pwd)"
DROP_ID="${1:?드롭 id를 입력하세요}"
PURCHASE_PATH="${2:-redis}"
POSTGRES_CONTAINER="${DROPGATE_LOAD_POSTGRES_CONTAINER:-dropgate-load-postgres}"
REDIS_CONTAINER="${DROPGATE_LOAD_REDIS_CONTAINER:-dropgate-load-redis}"
DATABASE_NAME="${DROPGATE_LOAD_DATABASE_NAME:-dropgate}"

case "$PURCHASE_PATH" in
  db|redis) ;;
  *) printf '구매 경로는 db 또는 redis여야 합니다\n' >&2; exit 2 ;;
esac

DB_REPORT="$(docker exec -i "$POSTGRES_CONTAINER" psql -h 127.0.0.1 -U postgres -d "$DATABASE_NAME" \
  -v ON_ERROR_STOP=1 -v drop_id="$DROP_ID" -tA <"$LOAD_DIR/sql/reconcile-purchase.sql")"
jq -e 'type == "object"' <<<"$DB_REPORT" >/dev/null
STOCK="$(docker exec "$REDIS_CONTAINER" redis-cli --raw GET "drop:$DROP_ID:stock")"
BUYERS="$(docker exec "$REDIS_CONTAINER" redis-cli --raw SCARD "drop:$DROP_ID:buyers")"
STREAM_LENGTH="$(docker exec "$REDIS_CONTAINER" redis-cli --raw XLEN orders.events)"
for value in "$STOCK" "$BUYERS" "$STREAM_LENGTH"; do
  if [[ ! "$value" =~ ^-?[0-9]+$ ]]; then
    printf '대조할 Redis 수량이 없거나 정수가 아닙니다: %s\n' "$value" >&2
    exit 1
  fi
done

REPORT="$(jq --arg path "$PURCHASE_PATH" --argjson stock "$STOCK" --argjson buyers "$BUYERS" \
  --argjson streamLength "$STREAM_LENGTH" '. + {
    purchasePath: $path,
    redisStock: $stock,
    buyers: $buyers,
    streamLength: $streamLength,
    redisSold: (.totalQuantity - $stock),
    redisStockDifference: (.totalQuantity - $stock - .confirmed),
    buyersDifference: ($buyers - .confirmed),
    dbStockDifference: (.totalQuantity - .dbRemainingQuantity - .confirmed)
  }' <<<"$DB_REPORT")"
printf '%s\n' "$REPORT"
jq -e '.duplicatedUsers == 0 and .confirmed <= .totalQuantity and (
  if .purchasePath == "redis" then
    .redisStockDifference == 0 and .buyersDifference == 0
  else
    .dbStockDifference == 0 and .redisStock == .totalQuantity and .buyers == 0
  end
)' <<<"$REPORT" >/dev/null
