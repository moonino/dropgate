# 02 Redis 키 설계

Redis 하나(논리 DB 0)를 세 서비스가 쓴다. 키 앞머리로 소유 서비스를 구분한다. 다른 서비스의 키를 읽거나 쓰지 않는다. 예외는 무효화 목록(auth가 쓰고 전 서비스가 읽는다)과 Streams(order가 쓰고 notification이 읽는다).

2주 차는 단일 Redis에 host와 port로 연결해 Lua와 장애 정책을 검증한다. 3주 차에 Sentinel 로컬 구성을 도입하고 클라이언트를 Sentinel 모드로 전환한다(docs/plan.md). Sentinel 마스터 이름은 `dropgate-master`다. 명령 타임아웃 200ms, 연결 타임아웃 1초. 쓰기는 마스터로만, 읽기도 마스터로(정합성 우선, 복제 지연 회피).

auth와 order의 공통 클라이언트 경계: Boot의 `LettuceClientOptionsBuilderCustomizer` 빈에서 Lettuce의 autoReconnect를 유지하고 replayFilter는 모든 명령에 true를 반환하며 disconnectedBehavior는 REJECT_COMMANDS로 둔다. 기존 소켓 연결 1초와 TimeoutOptions 설정을 보존한다. 앱에서도 Lua를 재시도하지 않는다. 통신 실패는 실행 여부가 불확실하므로 기존 판매 차단, 대조와 수동 복구 원칙을 따른다. 초기화나 INCR 보상을 재전송해 1회 권한과 수량을 깨뜨리지 않기 위한 설정이다. 근거는 [명령 실행 신뢰성](https://redis.github.io/lettuce/advanced-usage/command-execution-reliability/)과 [ClientOptions](https://redis.github.io/lettuce/advanced-usage/client-options/)다. 프로젝트 BOM이 해석한 Lettuce 7.5.2.RELEASE의 [DefaultEndpoint](https://raw.githubusercontent.com/redis/lettuce/7.5.2.RELEASE/src/main/java/io/lettuce/core/protocol/DefaultEndpoint.java)에서 연결 단절 시 pending과 endpoint 큐를 예외로 종료하는 경계를 확인했다. 후속 구현의 실제 단절 테스트로 고정한다.

## 1. auth-service

| 키 | 자료형 | 값 | TTL | 뜻 |
|---|---|---|---|---|
| `auth:refresh:{userId}:{jti}` | string | "1" | 14일 | 유효한 리프레시 토큰. 재발급 시 옛 키 삭제, 새 키 생성(회전) |
| `auth:revoked:{jti}` | string | "1" | 액세스 토큰 잔여 만료(최대 15분) | 로그아웃한 액세스 토큰. 전 서비스가 요청마다 EXISTS로 확인 |

로그아웃은 `logout.lua`로 revoked SET PX, refresh-index에 모은 jti의 refresh 키 DEL, index DEL을 원자 처리한다. 입력은 KEYS revoked와 refresh-index, ARGV 액세스 잔여 만료 ms와 refresh 키 앞머리 `auth:refresh:{userId}:`다. revoked만 쓰고 실패하면 같은 액세스 토큰이 401로 막혀 남은 리프레시를 지울 수 없으므로 한 스크립트로 묶는다.

스크립트가 index의 jti로 KEYS 밖 refresh 키를 만든다. 지금은 Sentinel이라 가능하지만 Cluster로 옮기면 관련 키를 같은 슬롯에 모으는 해시 태그 `{userId}`가 필요하다.

| 키 | 자료형 | 값 | TTL |
|---|---|---|---|
| `auth:refresh-index:{userId}` | set | jti 목록 | 14일, 갱신 |

측정: 로그아웃 뒤 세 서비스에서 같은 액세스 토큰이 401이 되는 시간. 각 서비스는 revoked 확인을 캐시하지 않는다(그래서 100ms 목표가 성립).

## 2. order-service

### 드롭 재고와 구매

| 키 | 자료형 | 값 | TTL | 뜻 |
|---|---|---|---|---|
| `drop:{dropId}:stock` | string(int) | 남은 수량 | close_at + 7일 | DB의 1회 초기화 권한을 커밋한 호출만 SET NX total_quantity. 키 유실 뒤 자동 재생성하지 않음 |
| `drop:{dropId}:buyers` | set | userId | close_at + 7일 | 구매 성공한 사용자. 1인 1회 1차 방어 |
| `drop:{dropId}:meta` | hash | status, open_at, close_at, rate | close_at + 7일 | status는 OPEN과 SOLD_OUT뿐. open_at과 close_at은 epoch ms 정수 문자열. CLOSED는 쓰지 않고 close_at으로 마감을 판정 |

### OPEN 초기화 (init.lua)

드롭 상태 스케줄러는 매 틱 DB 상태 전이 뒤 OPEN 드롭의 초기화 권한을 요청한다. 별도 DB 트랜잭션으로 `UPDATE drops SET stock_initialization_claimed=true WHERE id=? AND status='OPEN' AND stock_initialization_claimed=false`를 커밋하고, 영향 행이 1인 호출만 init.lua를 실행한다. claim은 Redis와 분리해 영속 저장하므로 여러 파드가 초기화해도 권한은 한 번뿐이다. 새 마이그레이션에서 기존 드롭은 claim=true로 backfill하고 이후 새 드롭의 기본값만 false로 둔다. 기존 드롭의 Redis 전환은 수동 복구 대상이다.

init.lua의 KEYS는 stock, meta이고 ARGV는 total_quantity, open_at ms, close_at ms, rate다. meta가 있으면 아무것도 하지 않고 0을 돌려준다. 없으면 meta를 HSET(status OPEN, open_at, close_at, rate), stock을 SET NX total_quantity 하고 두 키를 PEXPIREAT close_at + 7일로 맞춘 뒤 1을 돌려준다.

최초 초기화 실패도 실행 여부를 확신할 수 없으므로 claim을 유지하고 경고 로그를 남긴다. 다음 틱에 자동 재시도하지 않는다. 키가 없으면 판매를 차단해 수동 복구하고, 응답만 유실돼 이미 성공한 키가 있으면 기존 구매 계약대로 사용한다. claim=true 드롭의 Redis 키가 유실돼도 자동 재생성하지 않는다. 초기화 전이나 meta 유실 뒤 Redis 구매는 DROP_NOT_OPEN이다. `purchase-path=db`여도 같은 초기화 절차를 쓴다(ADR 0005).

수동 복구는 모든 주문 인스턴스를 중지하고 진행 중 DB 트랜잭션 종료와 기존 Redis 클라이언트 연결 및 미처리 쓰기 제거를 확인한 뒤 한다. CONFIRMED 주문 수로 stock을 total_quantity에서 그 수를 뺀 값으로, buyers를 해당 사용자 집합으로 복원하고 meta의 상태와 시각, rate 및 각 키의 TTL을 맞춘다. DB 판매 상태도 아래 동기화 규칙대로 같은 주문 수에 맞춘 뒤 재개한다. claim은 true로 유지한다. DB 건수만 보고 자동 초기화를 허용하지 않는다. 진행 중 주문이 아직 집계되지 않을 수 있어 가용성보다 초과 판매 방지를 택한다.

드롭 단건의 remainingQuantity는 stock GET, 목록은 페이지 항목의 stock을 MGET 한 번으로 읽는다. 키 없음(SCHEDULED, 초기화 전), Redis 실패, 200ms 초과면 해당 수량만 null이고 HTTP 200을 유지한다. DB 경로의 stock은 판매 때 줄지 않아 total_quantity에 머문다(ADR 0005).

### 접속 대기열과 입장

| 키 | 자료형 | 값 | TTL | 뜻 |
|---|---|---|---|---|
| `drop:{dropId}:queue` | zset | member userId, score 서버 시각 ms | close_at + 1시간 | 대기열. 클라이언트 시각을 받지 않는다 |
| `drop:{dropId}:admit:{userId}` | string | "1" | 60초 | 입장 허가. 구매 Lua가 EXISTS 확인 뒤 DEL |
| `drop:{dropId}:admit-lock` | string | 파드 ID | 1초 | 입장 스케줄러가 초마다 하나의 파드만 돌게 하는 락(SET NX PX 950) |
| `drop:{dropId}:admitted-count` | string(int) | 누계 | close_at + 7일 | 측정용. 초당 입장 속도 계산 |

입장 스케줄러(order 파드 안에서 1초마다): 락을 잡은 파드가 OPEN 드롭마다 `ZPOPMIN queue rate`로 rate명을 꺼내 각자 admit 키를 SET EX 60 하고 admitted-count를 INCRBY. 재고가 0이면 꺼내지 않는다(대기열에 남아 있다가 SOLD_OUT 응답을 받는다).

순번 조회: `ZRANK queue userId`가 null이면 admit 키 EXISTS로 ADMITTED인지 NOT_IN_QUEUE인지 가른다. 예상 대기 초는 rank / rate.

중복 진입: `ZADD NX`로 이미 있으면 점수를 바꾸지 않는다. 대기열 포기: `ZREM`.

### 구매 Lua 스크립트 계약 (purchase.lua)

KEYS[1] stock, KEYS[2] buyers, KEYS[3] admit:{userId}, KEYS[4] meta. ARGV[1] userId, ARGV[2] 현재 서버 시각 ms, ARGV[3] 입장 확인("1" 또는 "0").

`dropgate.order.queue.enabled`(`DROPGATE_QUEUE_ENABLED`, 기본 false)가 ARGV[3]을 정한다. 2주 차에는 false이고 3주 차 대기열 PR에서 기본을 true로 바꾼다. 대기열 전후 p99도 같은 스위치로 재고, false인 측정에는 대기열 비용이 없다고 적는다.

```
local status = HGET meta status
if not (status == 'OPEN' or status == 'SOLD_OUT') or ARGV[2] >= meta close_at then return {-1, 'DROP_NOT_OPEN'} end
if ARGV[3] == '1' and EXISTS admit == 0 then return {-2, 'NOT_ADMITTED'} end
if SISMEMBER buyers userId == 1 then return {-3, 'ALREADY_PURCHASED'} end
local stock = tonumber(GET stock)
if stock <= 0 then return {-4, 'SOLD_OUT'} end
DECR stock
SADD buyers userId
PEXPIREAT buyers (meta close_at + 7일)
if ARGV[3] == '1' then DEL admit end
if stock - 1 == 0 then HSET meta status 'SOLD_OUT' end
return {stock - 1, 'OK'}
```

meta가 없거나 status가 OPEN과 SOLD_OUT 중 하나가 아니어도 DROP_NOT_OPEN이다. SOLD_OUT도 입장, buyers, stock 검사를 이어간다. 입장 확인이 꺼져 있거나 입장 검사를 통과했으면 품절 뒤 기존 구매자는 ALREADY_PURCHASED, 신규 사용자는 SOLD_OUT이다. 입장 확인이 켜져 있고 admit 키가 없으면 buyers보다 먼저 NOT_ADMITTED다. DROP_NOT_OPEN 반환일 때만 서비스가 DB에서 드롭 존재를 확인해 없으면 404 DROP_NOT_FOUND, 있으면 409 DROP_NOT_OPEN으로 답한다.

반환 첫 값이 0 이상이면 성공이고 남은 수량이다. 성공 뒤에만 DB 트랜잭션(orders INSERT, order_ledger INSERT, outbox INSERT)을 연다. 본문의 쓰기를 flush하고 트랜잭션 경계에서 롤백 성공이 확인된 실패만 compensate.lua로 보상한다. INSERT 0행(UNIQUE)은 보상 뒤 409 ALREADY_PURCHASED, 그 밖의 확인된 롤백은 보상 뒤 503 ORDER_PERSIST_FAILED다. 커밋 중이나 커밋 후 예외, 롤백 실패처럼 결과가 불확실하면 재고와 buyers를 유지하고 자동 보상, Lua 재실행과 상태 동기화를 하지 않는다. 이때도 503 ORDER_PERSIST_FAILED와 Retry-After 2이며 실제 주문은 확정됐을 수 있다(ADR 0021). `dropgate_order_commit_unknown_total{drop}`과 식별자를 담은 error 로그를 남기고 1회 초기화 권한 절의 수동 복구 절차로 대조한다. 보상 실패는 `dropgate_stock_compensation_failed_total{drop}`과 error 로그에 남기고 응답 코드는 유지한다. 대조 배치가 불일치를 잡는다.

모든 성공 구매 확정, 성공한 compensate.lua, 성공한 cancel.lua 뒤에는 아래 DB 판매 상태 동기화를 한다. Redis 경로는 remaining_quantity를 바꾸지 않는다.

모든 키가 같은 드롭 아래라 Cluster로 가도 해시 태그 `{dropId}`로 같은 슬롯에 둘 수 있다. 지금은 Sentinel이라 상관없다.

### DB 저장 실패 보상 (compensate.lua)

KEYS stock, buyers, meta. ARGV userId. stock 또는 meta가 없으면 아무 쓰기도 하지 않고 -1을 반환한다. 둘 다 있으면 INCR stock, SREM buyers userId를 하고 INCR 결과가 1이며 meta status가 SOLD_OUT이면 OPEN으로 되돌린다. 성공은 복구한 수량(1 이상)을 반환한다. 마지막 재고의 DB 저장 실패 뒤 복구한 재고와 meta 상태가 불일치하는 것을 막는다.

### 취소 보상 Lua (cancel.lua)

KEYS stock, buyers, meta. ARGV userId. stock 또는 meta가 없으면 아무 쓰기도 하지 않고 -1을 반환한다. 둘 다 있으면 INCR stock, SREM buyers userId를 하고 meta status가 SOLD_OUT이면 OPEN으로 바꿔 1, 아니면 0을 돌려준다. 0 또는 1인 성공 뒤에만 DB 판매 상태를 동기화한다. buyers에서 빼도 DB UNIQUE(drop_id, user_id)가 남아 취소한 사용자의 재구매는 DB에서 막힌다.

래퍼는 두 스크립트의 -1을 보상 실패로 다룬다. 기존 응답 코드를 유지하고 같은 `dropgate_stock_compensation_failed_total{drop}`과 error 로그를 남기며 상태 동기화는 하지 않는다. 키 유실 뒤 stock, meta, buyers를 재생성하거나 바꾸지 않고 수동 복구에 맡긴다.

### DB 판매 상태 동기화

`synchronizeSaleStatus(dropId)`는 새 DB 트랜잭션에서 먼저 drops 행을 FOR UPDATE로 잠근다. 잠금을 얻은 뒤 별도 문장의 새 READ COMMITTED 스냅샷으로 CONFIRMED 주문 수를 읽는다. 수가 total_quantity와 같으면 SOLD_OUT, 작으면 OPEN으로 갱신한다. CLOSED, SCHEDULED와 remaining_quantity는 유지한다. 상태 갱신은 모두 이 잠금으로 직렬화하고 실패는 경고 로그만 남긴다.

마지막 구매만 호출하면 앞서 예약한 구매가 늦게 확정된 뒤 DB가 OPEN으로 남을 수 있다. 단순한 조건부 상태 UPDATE도 지연된 구매 갱신이 취소를 덮거나 보상 뒤 SOLD_OUT이 남는 경쟁을 막지 못한다. 모든 성공 확정과 보상, 취소가 같은 동기화를 호출한다(ADR 0007).

`dropgate.order.purchase-path`(`DROPGATE_PURCHASE_PATH`)는 redis(기본) 또는 db로 구매와 취소 구현을 함께 고른다. 바꿔 끼우는 단위는 구매 흐름 전체다(ADR 0005).

### Redis 전체 장애 정책

구매 경로에서 Redis 명령이 실패하거나 200ms를 넘으면 503 `STOCK_UNAVAILABLE`과 `Retry-After: 2`를 돌려준다. DB 경로로 폴백하지 않는다(ADR 0006). 대기열 진입과 순번 조회도 503 `STOCK_UNAVAILABLE`. 토큰 검증, 재발급, 로그아웃의 Redis 실패는 같은 정책에 코드만 503 `AUTH_UNAVAILABLE`이다. 드롭 목록과 단건 조회는 Redis를 읽지 못하면 remainingQuantity만 null로 답하고 계속 동작한다. JWKS, 카카오 로그인 시작(302, OAuth state는 Redis가 아니라 서명된 쿠키, ADR 0003)도 계속 동작한다. 로그인 콜백과 재발급은 인증이 없어도 리프레시를 Redis에 쓰므로 503이고, 내 주문 조회는 DB만 보지만 Bearer 검증이 Redis를 거치므로 함께 503이다.

## 3. Streams (order가 쓰고 notification이 읽는다)

| 키 | 자료형 | 뜻 |
|---|---|---|
| `orders.events` | stream | 주문 이벤트. XADD MAXLEN 근사 트리밍, 상한 100만 건 |
| `orders.events.dlq` | stream | 3회 실패한 메시지. 원본 필드 + error, failed_at |

소비자 그룹 `notification`, 소비자 이름은 파드 호스트명. `XREADGROUP GROUP notification {pod} COUNT 100 BLOCK 2000 STREAMS orders.events >`. 처리 성공 시 `XACK`. 실패 시 ACK하지 않고 재시도. `XPENDING`으로 idle 30초 넘은 메시지를 `XCLAIM`해 다른 파드가 가져간다(죽은 파드 복구). delivery count가 3을 넘으면 DLQ에 XADD 뒤 XACK.

멱등은 소비자 측 Redis 키가 아니라 DynamoDB 조건부 쓰기로 한다(03 문서). 메시지 필드는 04 문서.

릴레이(order 파드 안, 500ms마다, FOR UPDATE SKIP LOCKED로 100건): `XADD orders.events * {fields}` 성공하면 outbox PUBLISHED. XADD 성공 뒤 DB 갱신 전에 죽으면 같은 outbox 행이 다시 발행된다. 그래서 메시지에 event_id(outbox id)를 싣고 소비자가 멱등 처리한다.

## 4. 측정용 키

| 키 | 뜻 |
|---|---|
| `drop:{dropId}:admitted-count` | 초당 입장 속도 |
| `metrics:purchase:rejected:{reason}` | 사유별 거절 카운터(측정 뒤 삭제) |

## 5. Sentinel 구성

로컬 compose와 kind(Bitnami redis 차트 sentinel.enabled): 마스터 1, 복제 2, 센티넬 3, quorum 2, down-after-milliseconds 5000, failover-timeout 10000. 복제는 비동기. `min-replicas-to-write 1`과 `min-replicas-max-lag 10`을 켜서 복제본이 전부 끊기면 쓰기를 거절한다(유실 창을 줄이는 대신 가용성을 조금 내준다. ADR 0010에 전후 수치).

EKS: ElastiCache 복제 그룹, 노드 2개 2 AZ, 자동 장애 조치 켬, 엔진 Redis 7 또는 Valkey. Lettuce는 구성 엔드포인트로 붙는다.

측정: 마스터 종료 뒤 첫 성공 쓰기까지 초, 그동안 503 수, 절체 뒤 `stock + SCARD buyers`와 DB `COUNT(*) WHERE status='CONFIRMED'`의 차.
