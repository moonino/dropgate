# 취소 시각을 recordCanceled 인자로 따로 넘기지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-17, Claude Code 자동 리뷰 제안 |
| 제안 요지 | `OrderHistoryRecorder.recordCanceled`가 `checkNotNull(order.canceledAt)`로 취소 시각을 다시 꺼내지 말고 `OrderCancelService.cancel`이 가진 `canceledAt`을 인자로 받아 null 처리를 없앤다. |
| 왜 틀렸나 | 설계 불일치. 04 이벤트 명세는 메시지의 occurred_at을 order_ledger.occurred_at으로 정하고 같은 메시지의 order.canceled 페이로드에 canceledAt을 싣는다. 08 4절 2단계는 orders.canceled_at, 원장 CANCELED 항목, outbox order.canceled를 한 트랜잭션에 남긴다. 설계 문서는 이 값들이 같아야 한다고 문자 그대로 쓰지 않는다. 다만 모두 한 취소의 시각을 뜻하고, PR #83 본문은 원장 occurred_at이 주문의 canceled_at과 같다고 정했다. 지금은 원장 occurred_at과 페이로드 canceledAt(`OrderEventPayload.createFrom(order, dropName)`)을 모두 엔티티의 canceledAt 한 출처에서 읽는다. 제안대로 하면 원장은 인자에서, 페이로드는 엔티티에서 시각을 읽어 출처가 둘이 된다. 지금 호출부에서는 두 값이 같다. 하지만 호출부가 바뀌면 갈라질 수 있다. 제안 코드가 설계 문서와 직접 어긋나지는 않아서 README 일곱 원인 중 정확히 맞는 것은 없다. 설계가 한 메시지로 묶은 시각들을 한 출처로 지키는 구조를 깨는 제안이라 가장 가까운 설계 불일치를 골랐다. |
| 어떻게 알았나 | 설계 문서 대조. [리뷰 제안 2번](https://github.com/moonino/dropgate/pull/83#issuecomment-6230597517)을 04 메시지 필드 표, 08 4절, PR #83 본문, 브랜치 feat/9-order-cancel의 `OrderCancelService.cancel`, `OrderHistoryRecorder.record`, `OrderEventPayload.createFrom`에 대조했다. 원장과 페이로드가 모두 엔티티의 canceledAt을 읽는 것을 확인했다. 제안을 적용해 실행하지는 않았다. 테스트 "취소 원장 항목의 시각은 주문의 취소 시각과 같다"는 원장 occurred_at과 orders.canceled_at이 같은지만 본다. 제안대로 해도 두 값은 같은 canceledAt에서 오므로 이 테스트로는 차이가 드러나지 않는다. 페이로드 canceledAt과의 일치는 테스트로 고정되어 있지 않다. |
| 대신 한 것 | `recordCanceled`가 엔티티의 canceledAt을 읽고 취소 전 호출을 checkNotNull로 막는 형태를 유지한다. PR #83 |
