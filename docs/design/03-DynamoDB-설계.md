# 03 DynamoDB 설계

테이블 하나 `dropgate-notifications`. 접근 패턴이 하나(사용자별 최신순 피드)라 단일 테이블, GSI 없음. 로컬과 kind는 DynamoDB Local, EKS는 온디맨드 테이블을 Terraform으로 만들고 IRSA로 notification 파드만 접근한다.

## 키

| 속성 | 타입 | 값 | 뜻 |
|---|---|---|---|
| pk | S | `USER#{userId}` | 파티션 키 |
| sk | S | `NOTI#{occurredAtIso}#{eventId}` | 정렬 키. ISO 8601 UTC 밀리초, 뒤에 outbox id를 붙여 유일 |

같은 밀리초에 이벤트 둘이 와도 eventId로 갈린다. 최신순은 `ScanIndexForward=false`.

## 속성

| 속성 | 타입 | 뜻 |
|---|---|---|
| event_id | N | outbox id. 멱등 조건에 쓴다 |
| type | S | ORDER_CONFIRMED, ORDER_CANCELED |
| drop_id | S | |
| drop_name | S | 이벤트에 실려 온 값. 다른 서비스 조회 금지 |
| order_id | S | |
| message | S | 표시 문장. "한정판 A 구매가 확정됐습니다" |
| read | BOOL | 기본 false. 읽음 처리 API는 범위 밖, 속성만 둔다 |
| created_at | S | 알림 저장 시각 |
| expires_at | N | epoch 초, 90일 뒤. 테이블 TTL 속성 |

## 멱등 쓰기

같은 이벤트가 두 번 와도(릴레이 중복 발행, 소비자 재시도) 알림은 하나여야 한다. sk에 eventId가 들어 있으므로 `PutItem`에 `ConditionExpression: attribute_not_exists(pk) AND attribute_not_exists(sk)`를 건다. 조건 실패(ConditionalCheckFailedException)는 성공으로 취급하고 XACK 한다. 소비자 측 Redis 멱등 키가 필요 없다.

## 조회

`Query pk = USER#{userId}, ScanIndexForward=false, Limit=20, ExclusiveStartKey=cursor`. 커서는 마지막 sk를 base64로 감싼 값. 응답에 next_cursor를 준다.

## 용량과 비용

온디맨드. 쓰기 1만 건은 약 12.5 WCU 초 환산으로 수십 원. 7주 차 한 주 수백 원 추정.

## PostgreSQL JSONB와의 비교 (ADR 0011용 측정)

같은 데이터를 orders 스키마 밖 별도 스키마의 `notifications_jsonb (user_id, occurred_at, event_id, body jsonb)` 테이블에 넣고 `(user_id, occurred_at DESC)` 인덱스로 같은 쿼리를 돌려 쓰기 1만 건 시간과 조회 p99를 한 줄 비교한다. 결론이 "차이 없음"이면 그대로 적는다. 선택 이유는 성능이 아니라 접근 패턴 하나에 맞는 키 설계와 TTL과 운영 부담 0이다.
