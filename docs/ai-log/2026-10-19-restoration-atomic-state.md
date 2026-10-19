# 복구된 드롭의 원자 상태를 함께 검증한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | assertRestored의 재고와 구매자 복구, OPEN 상태 단언을 나눈다. |
| 왜 틀렸나 | 과잉. 한 Lua 호출이 재고, 구매자와 판매 메타데이터를 함께 복구한다. 일부만 복구한 상태를 놓치지 않도록 같은 드롭의 원자 복구 결과를 함께 검증한다. 일반 재고와 마지막 재고는 별도 시나리오로 이미 구별한다. |
| 어떻게 알았나 | 설계 문서 대조, 테스트. [리뷰 제안](https://github.com/moonino/dropgate/pull/144#issuecomment-6261151270)을 02 문서의 원자 복구 결과와 실제 Redis 상태에 비교했다. JDK 21의 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 ktlintCheck :common:test :order-service:test`에서 common 66개와 order 220개가 통과했다. |
| 대신 한 것 | 같은 복구 결과의 세 속성을 유지하고 키 유실 시 쓰기 금지는 별도 테스트로 검증한다. PR #144. |
