# 실패 기록 명령은 상태를 반환하지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | recordFailure가 이번 호출로 FAILED가 됐는지 Boolean으로 반환해 서비스의 status 확인을 없앤다. |
| 왜 틀렸나 | 과잉. 임계값 계산은 엔티티 한곳에만 있고 서비스는 결정된 상태에 따라 계측한다. attempts와 최대 횟수를 다시 비교하지 않으므로 같은 판단을 중복하지 않는다. Boolean 반환도 가능한 설계지만 상태 변경 명령에 조회 결과를 더할 이득이 없고 현재 관심사 분리가 명확해 보류한다. |
| 어떻게 알았나 | 설계 문서 대조. [리뷰 제안](https://github.com/moonino/dropgate/pull/139#issuecomment-6260917694)을 OutboxEntity.recordFailure와 OutboxRelayService.recordFailure, 04 문서의 영구 실패 상태와 계측에 비교했다. 최대 횟수 10은 엔티티 상수 한곳이며 JDK 21의 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 ktlintCheck :order-service:test`로 전체 order 189개가 통과했다. |
| 대신 한 것 | 엔티티는 실패를 기록하고 서비스는 상태에 따라 카운터와 로그를 남긴다. PR #139. |
