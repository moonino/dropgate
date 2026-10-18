# 구매 INSERT에 고정한 CONFIRMED를 파라미터로 받지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-17, Claude Code 자동 리뷰 제안 |
| 제안 요지 | OrderRepository의 INSERT에 고정한 `'CONFIRMED'`를 `quantity`처럼 `:status` 파라미터로 받아 `order.status.name`을 넘기면 주문 상태를 정하는 곳이 `OrderEntity.confirm` 하나로 준다. |
| 왜 틀렸나 | 설계 불일치. 동작 버그를 고치는 제안은 아니고, 설계가 구매 INSERT를 CONFIRMED로 정해 둔 계약을 SQL 밖으로 여는 변경이라 쓰지 않았다. docs/design/08-시퀀스-흐름.md 2절 4번은 구매 성공을 `orders INSERT CONFIRMED`로 정했고, docs/design/01-ERD.md의 orders 트리거는 UPDATE(CONFIRMED -> CANCELED만 허용)와 DELETE만 지키며 INSERT를 지키는 트리거는 없다. 메서드 이름 `insertConfirmedUnlessPurchased`와 SQL 리터럴이 같은 계약을 말하는데, status를 받으면 호출하는 쪽이 CANCELED를 넘길 수 있는 입구가 새로 생긴다. 지금 SQL은 canceled_at을 넣지 않으므로 그런 삽입은 `CHECK ((status = 'CANCELED') = (canceled_at IS NOT NULL))`에 걸리지만, 이는 상태 값을 지키려고 둔 장치가 아니다. |
| 어떻게 알았나 | 설계 문서 대조와 문서 대조. [리뷰 제안 2번](https://github.com/moonino/dropgate/pull/79#issuecomment-6230588554)을 08-시퀀스-흐름.md의 구매 흐름, 01-ERD.md의 orders 제약과 트리거 정의, 브랜치 feat/9-purchase-confirm의 OrderRepository.kt, V1__create_orders_schema.sql, V3__create_orders_triggers.sql에 대조했다. 같은 날 다음 차례 [리뷰 제안 2번](https://github.com/moonino/dropgate/pull/79#issuecomment-6233140301)도 같은 내용이라 같은 이유로 기각했다. 기각 이유는 PR #79 본문의 자동 리뷰 반영 문단에 적었다. 실행이나 측정은 하지 않았다. |
| 대신 한 것 | SQL의 `'CONFIRMED'` 리터럴과 메서드 이름 `insertConfirmedUnlessPurchased`를 그대로 둔다. PR #79 |
