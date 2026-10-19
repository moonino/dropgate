# 02 Redis 키 설계

Redis 하나(논리 DB 0)를 세 서비스가 쓴다. 키 앞머리로 소유 서비스를 구분한다. 다른 서비스의 키를 읽거나 쓰지 않는다. 예외는 무효화 목록(auth가 쓰고 전 서비스가 읽는다)과 Streams(order가 쓰고 notification이 읽는다).

Sentinel 마스터 이름 `dropgate-master`. 클라이언트는 Lettuce Sentinel 모드. 명령 타임아웃 200ms, 연결 타임아웃 1초. 쓰기는 마스터로만, 읽기도 마스터로(정합성 우선, 복제 지연 회피).

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
| `drop:{dropId}:stock` | string(int) | 남은 수량 | close_at + 7일 | DB OPEN 드롭을 매 틱 init.lua로 멱등 초기화(SET NX total_quantity) |
| `drop:{dropId}:buyers` | set | userId | close_at + 7일 | 구매 성공한 사용자. 1인 1회 1차 방어 |
| `drop:{dropId}:meta` | hash | status, open_at, close_at, rate | close_at + 7일 | status는 OPEN과 SOLD_OUT뿐. open_at과 close_at은 epoch ms 정수 문자열. CLOSED는 쓰지 않고 close_at으로 마감을 판정 |

### OPEN 초기화 (init.lua)

드롭 상태 스케줄러는 매 틱 DB 상태 전이 뒤 DB status가 OPEN인 드롭마다 init.lua를 부른다. KEYS stock, meta. ARGV total_quantity, open_at ms, close_at ms, rate. meta가 있으면 아무것도 하지 않고 0을 돌려준다. 없으면 meta를 HSET(status OPEN, open_at, close_at, rate), stock을 SET NX total_quantity 하고 두 키를 PEXPIREAT close_at + 7일로 맞춘 뒤 1을 돌려준다.

Redis 실패는 경고 로그만 남기고 다음 틱에 다시 시도한다. 초기화 전 구매는 DROP_NOT_OPEN이다. `purchase-path=db`여도 초기화한다(ADR 0005).

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

meta가 없거나 status가 OPEN과 SOLD_OUT 중 하나가 아니어도 DROP_NOT_OPEN이다. SOLD_OUT도 입장, buyers, stock 검사를 이어가므로 품절 뒤 기존 구매자는 ALREADY_PURCHASED, 신규 사용자는 SOLD_OUT이다. 이 반환일 때만 서비스가 DB에서 드롭 존재를 확인해 없으면 404 DROP_NOT_FOUND, 있으면 409 DROP_NOT_OPEN으로 답한다.

반환 첫 값이 0 이상이면 성공이고 남은 수량이다. 성공 뒤에만 DB 트랜잭션(orders INSERT, order_ledger INSERT, outbox INSERT)을 연다. INSERT 0행(UNIQUE)이면 compensate.lua 뒤 409 ALREADY_PURCHASED, DB 예외면 compensate.lua 뒤 503 ORDER_PERSIST_FAILED다. 보상 실패는 `dropgate_stock_compensation_failed_total{drop}`과 error 로그에 남기고 응답 코드는 유지한다. 대조 배치가 불일치를 잡는다.

남은 수량이 0이면 별 트랜잭션으로 `UPDATE drops SET status='SOLD_OUT' WHERE id=? AND status='OPEN'`을 한다. 실패는 경고 로그만 남긴다. Redis 경로는 remaining_quantity를 바꾸지 않는다.

모든 키가 같은 드롭 아래라 Cluster로 가도 해시 태그 `{dropId}`로 같은 슬롯에 둘 수 있다. 지금은 Sentinel이라 상관없다.

### DB 저장 실패 보상 (compensate.lua)

KEYS stock, buyers, meta. ARGV userId. INCR stock, SREM buyers userId를 하고 INCR 결과가 1이며 meta status가 SOLD_OUT이면 OPEN으로 되돌린다. 마지막 재고의 DB 저장 실패 뒤 복구한 재고와 meta 상태가 불일치하는 것을 막는다.

### 취소 보상 Lua (cancel.lua)

KEYS stock, buyers, meta. ARGV userId. INCR stock, SREM buyers userId를 하고 meta status가 SOLD_OUT이면 OPEN으로 바꿔 1, 아니면 0을 돌려준다. 1이면 별 트랜잭션으로 `UPDATE drops SET status='OPEN' WHERE id=? AND status='SOLD_OUT'`을 한다. 이 UPDATE 실패는 경고 로그만 남긴다. buyers에서 빼도 DB UNIQUE(drop_id, user_id)가 남아 취소한 사용자의 재구매는 DB에서 막힌다.

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
