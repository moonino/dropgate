# 실제 단절 실패의 원인 스택을 검증 메시지에 유지한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | 발행 재정의 안의 예외 수집과 failures 목록을 제거하고 발행 횟수와 중단 호출만 남긴다. |
| 왜 틀렸나 | 과잉. 수집된 예외는 별도 동작이 아니라 attempts 보존 단언이 실패했을 때 실제 Redis cause 계층을 표시하는 진단 자료다. 실제 단절에서 Spring의 RedisSystemException이 Lettuce의 정확한 RedisException을 감쌌던 경계를 고정하므로, 라이브러리나 연결 경로가 바뀌어 일반 실패로 잘못 분류되면 실제 원인을 함께 보여 주는 편이 원인 파악에 도움이 된다. |
| 어떻게 알았나 | 테스트. [리뷰 제안](https://github.com/moonino/dropgate/pull/142#issuecomment-6261092744)을 실제 failures 수집과 withFailMessage의 사용에 대조했다. 수집한 예외는 그대로 다시 던져 처리 흐름을 바꾸지 않는다. JDK 21의 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 ktlintCheck :order-service:test`에서 실제 중단과 정지 테스트를 포함한 order 193개가 통과했다. |
| 대신 한 것 | 발행 횟수와 상태 단언은 그대로 두고 실패 때 원인 스택을 표시하는 작은 진단 코드를 유지한다. PR #142. |
