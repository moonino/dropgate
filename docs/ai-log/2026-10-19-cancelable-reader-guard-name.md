# 취소 준비 값을 읽는 역할의 이름을 유지한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | [PR #157 제안](https://github.com/moonino/dropgate/pull/157#issuecomment-6262491088)은 CancelableOrderReader가 잠금과 소유자 및 상태와 마감 검사를 수행하므로 OrderCancellationGuard 등 검증 역할을 드러내는 이름을 검토하자는 내용이다. |
| 왜 틀렸나 | 과잉. 가능한 명명 제안이지만 현재 공개 메서드 lockCancelableOrder가 잠금을 잡고 취소 가능한 값을 준비한다는 뜻을 드러낸다. 클래스는 검사만 하는 것이 아니라 주문과 드롭 이름을 읽고 취소 시각을 담은 값을 반환한다. Guard가 이 전체 역할을 더 정확히 가리킨다고 단정하기 어렵다. 현재 기능 오류가 없는 마지막 검증 단계에서 명명 범위를 다시 넓히지 않는다. |
| 어떻게 알았나 | 테스트와 설계 문서 대조. 행 잠금 뒤 시각 획득과 취소 검사 순서를 08 문서와 대조했다. 동일 생산 소스를 포함한 초기 순수 리팩터에서 JDK 21로 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 --no-parallel ktlintCheck :common:test :order-service:test`로 common 66개와 order-service 294개가 통과했다. 마지막 테스트 조립 뒤에도 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 --no-parallel :order-service:test --tests 'dropgate.order.RedisPersistenceResponseTest' --tests 'dropgate.order.StockUnavailableResponseTest' --tests 'dropgate.order.StockTimeoutResponseTest'`의 실제 HTTP 5개가 통과했다. 실패, 오류와 건너뜀은 모두 0이다. 이 제안 때문에 소스를 바꾸거나 테스트를 재실행하지 않았다. |
| 대신 한 것 | CancelableOrderReader와 lockCancelableOrder를 유지하고 검증된 head 34eeaf5를 보존한다. [PR #157](https://github.com/moonino/dropgate/pull/157). |
