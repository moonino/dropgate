# 외부 Redis 예외 분류를 한 predicate로 유지한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | 정확한 RedisException cause 검사 부분을 isLettuceDisconnected 함수로 추출한다. |
| 왜 틀렸나 | 과잉. 추출은 가능한 가독성 개선이지만 현재 isUnavailable도 외부 예외 분류 한 개념을 여섯 줄에 모은다. 정확한 base RedisException은 이번 버전에서 관찰한 단절 경로를 판별하는 조건이며 단절과 동치인 공개 API는 아니다. 새 이름이 그보다 강한 의미를 보장하지 않으므로 지금 구조를 유지한다. |
| 어떻게 알았나 | 테스트. [리뷰 제안](https://github.com/moonino/dropgate/pull/142#issuecomment-6261092744)을 Spring Data Redis 4.1.1의 변환기와 Lettuce 7.5.2의 실제 단절 cause에 대조했다. RedisCommandExecutionException이 있으면 제외하고 연결 실패와 타임아웃 타입을 별도로 분류한다. JDK 21의 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 ktlintCheck :order-service:test`에서 실제 단절, 정지, 연결 거절과 WRONGTYPE를 포함한 order 193개가 통과했다. |
| 대신 한 것 | 실제 경로와 버전 의존 조건은 PR 본문에 기록하고, 외부 예외 분류는 작은 isUnavailable에 유지한다. PR #142. |
