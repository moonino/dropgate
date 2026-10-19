# 타임아웃 안의 모름 응답을 함께 검증한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | 응답 없는 Redis 테스트에서 모름 반환과 시간 단언을 별도 테스트로 나누고 도우미에서 시간 단언을 없앤다. |
| 왜 틀렸나 | 과잉. 응답 없는 Redis 조회를 200ms 근처에서 모름으로 끝낸다는 하나의 장애 응답 계약이다. 반환만 확인하면 긴 대기를 놓치고 시간만 확인하면 실패 대신 예외를 던지는 구현을 놓친다. |
| 어떻게 알았나 | 테스트. [리뷰 제안](https://github.com/moonino/dropgate/pull/136#issuecomment-6260937139)을 실제 정지한 전용 Redis의 모름 반환과 경과 시간 단언에 대조했다. 합성 장애 시험으로 JDK 21의 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 ktlintCheck :order-service:test`를 실행했을 때 GET 206.259ms와 MGET 204.778ms 뒤 모름을 반환했다. 부하 성능 수치나 정밀한 지연 상한은 아니다. 도우미는 두 읽기 경로에 같은 정지와 정리 절차를 적용한다. |
| 대신 한 것 | 복합 장애 응답 계약을 한 테스트에 유지하고 명령 타임아웃 설정은 RedisConnectionTest에서 별도로 검증한다. PR #136 |
