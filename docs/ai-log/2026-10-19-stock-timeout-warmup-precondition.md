# 지연 시험의 정상 재고 전제를 확인한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안 |
| 제안 요지 | Redis 정지 전 준비 HTTP 요청에서 수량 단언을 빼고 200만 확인한다. |
| 왜 틀렸나 | 과잉. 준비 단언 제거는 가능한 정리지만 전용 Redis와 실제 HTTP 조회가 정상 연결됐다는 사전 조건을 먼저 확인한다. 이후 같은 경로에서 정지 때문에 null로 바뀌는 장애 계약을 검증한다. |
| 어떻게 알았나 | 테스트. [리뷰 제안](https://github.com/moonino/dropgate/pull/141#issuecomment-6261057359)을 준비 요청이 같은 전용 Redis의 정상 수량을 읽는 조건과 비교했다. 구현 전 아홉 HTTP 테스트 중 수량 연결 세 개와 이 준비 수량 두 개가 실패했다. `./gradlew ktlintCheck :order-service:test`의 order 202개가 통과했다. [리뷰](https://github.com/moonino/dropgate/pull/141#issuecomment-6261057359). |
| 대신 한 것 | 전용 재고 수량을 읽는 사전 조건을 유지한다. PR #141. |
