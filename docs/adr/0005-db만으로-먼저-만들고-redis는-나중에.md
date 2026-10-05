# 0005 DB만으로 먼저 만들고 Redis는 나중에

- 상태: accepted
- 날짜: 2026-10-04
- 관련: docs/design/01-ERD.md drops.remaining_quantity와 orders UNIQUE, docs/design/08-시퀀스-흐름.md 2절 "1주 차 DB만 경로", docs/plan.md 2절과 3절 1주, 이슈 #11

## 맥락

Redis 측정 질문의 첫 줄은 "재고 100개에 1만 요청을 쏘면 성공이 정확히 100건인가"이고 그 다음이 "대기열 전과 후에 p99가 어떻게 달라지는가"다. 둘 다 전후 비교다. Redis를 처음부터 넣으면 "Redis 덕분에 빨라졌다"를 보일 기준선이 없다. 또 1주 차에는 인증과 모듈 뼈대만으로도 일이 많아 Redis까지 넣을 여유가 없다.

## 결정

1주 차 구매는 PostgreSQL만으로 만든다. 2주 차에 Redis Lua 경로로 바꾸되 DB 경로는 플래그로 남겨 기준선을 다시 잴 수 있게 한다.

- 1주 차 구매는 한 트랜잭션이다. `UPDATE drops SET remaining_quantity = remaining_quantity - 1 WHERE id = ? AND remaining_quantity > 0 AND status = 'OPEN'` 뒤 영향 행이 1이면 orders INSERT, 0이면 드롭 상태를 읽어 SCHEDULED나 CLOSED면 DROP_NOT_OPEN, OPEN이나 SOLD_OUT이면 SOLD_OUT으로 답한다. 1인 1회는 `UNIQUE (drop_id, user_id)`가 막고, 위반이면 차감까지 함께 롤백한 뒤 ALREADY_PURCHASED로 답한다. 행 잠금 하나와 유니크 제약만으로 정확도는 보장된다. 느릴 뿐이다. 다만 차감이 UNIQUE 확인보다 먼저라, 이미 산 사용자가 품절 뒤 요청하면 Redis 경로와 달리 ALREADY_PURCHASED가 아니라 SOLD_OUT을 받는다. 정확도에는 영향이 없고 응답 코드 분포만 다르므로 기준선 측정 기록에 함께 적는다.
- 이 느림이 기준선이다. 서비스 1개에서 재고 100개에 1만 요청을 쏜 정확도와 p99를 1주 차 끝에 기록하고, 2주 차 Redis 뒤에 같은 명령으로 다시 잰다.
- DB 경로를 지우지 않고 플래그로 남기는 이유는 두 가지다. 파드 3개 환경(6주 차)에서 기준선을 다시 재야 비교가 공정하고, Redis 전체 장애 때 막기로 한 정책(ADR 0006)을 8주 차 버퍼에서 DB 폴백과 수치로 비교하려면 DB 경로가 남아 있어야 한다.
- 2주 차 이후 재고의 정본은 Redis다. `drops.remaining_quantity`는 DB 경로 플래그가 켜졌을 때만 갱신하고, 대조 배치는 이 컬럼이 아니라 orders 건수를 센다(ERD에 명시).

## 대안

| 대안 | 장점 | 단점 | 안 고른 이유 |
|---|---|---|---|
| 처음부터 Redis Lua | 1주 차 코드를 2주 차에 버리지 않는다 | 전후 비교 기준선이 없다. 1주 차 작업량이 넘친다 | 측정이 목표라 기준선이 먼저다 |
| SELECT FOR UPDATE 뒤 UPDATE | 명시적이라 읽기 쉽다 | 왕복이 둘이고 조건부 UPDATE 한 번과 결과가 같다 | 더 느린 기준선을 만들 이유가 없다 |
| 낙관적 락(version 컬럼) | 잠금 대기가 없다 | 1만 동시 요청에서 재시도 폭풍이 난다. 기준선이 "재시도 전략" 수치로 오염된다 | 비교 대상은 잠금 방식이 아니라 Redis 유무다 |
| 2주 차에 DB 경로 삭제 | 코드가 단순해진다 | 파드 3개 기준선 재측정과 폴백 논의가 불가능해진다 | 플래그 하나의 비용이 그보다 싸다 |

## 결과

- `remaining_quantity`가 2주 차부터는 정본이 아닌 컬럼으로 남는다. 읽는 쪽이 헷갈리지 않게 ERD에 역할을 적어 두었고, 드롭 단건 조회의 remainingQuantity는 Redis에서 읽는다.
- DB 경로 플래그가 켜진 동안 Redis stock은 줄지 않는다. 2주 차 이후에는 OPEN 전이 때 stock 키가 total_quantity로 초기화되므로(02 문서), 그 드롭의 단건 조회 remainingQuantity는 total_quantity에 머물고 대조 diff는 판매 수만큼 음수로 난다. 그래서 DB 경로 측정은 전용 드롭으로 하고, 측정이 끝나면 그 드롭을 CLOSED로 바꿔 대조 범위에서 빠지게 한다(06 문서). 1주 차에는 Redis가 없어 remainingQuantity는 API 명세대로 null이다.
- 플래그 분기 하나가 구매 흐름에 생긴다. 두 경로는 트랜잭션 모양이 다르므로(DB 경로는 차감과 주문이 한 트랜잭션, Redis 경로는 Lua 뒤 트랜잭션과 보상) 바꿔 끼우는 단위는 재고가 아니라 구매 흐름 전체다. 구현체 둘을 설정으로 골라 if 문이 퍼지지 않게 하고, 클래스 구조는 구현 때 정한다.
- 1주 차에는 대기열이 없으므로 입장 확인을 건너뛴다. 기준선 수치에는 대기열 비용이 포함되지 않는다는 점을 측정 기록에 적는다.
- 측정으로 확인할 것: DB만으로 서비스 1개에서 재고 100개 1만 요청의 초과 수, 중복 수, p99. 같은 명령을 2주 차 Redis 경로와 6주 차 파드 3개에서 반복한다.
