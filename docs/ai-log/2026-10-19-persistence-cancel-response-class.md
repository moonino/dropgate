# 저장 흐름의 취소 응답을 별도 클래스로 나누지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | [PR #155 제안](https://github.com/moonino/dropgate/pull/155#issuecomment-6262136233)은 RedisPersistenceResponseTest의 취소 뒤 복구 실패 200 테스트를 RedisCancelResponseTest라는 별도 클래스로 옮기자는 내용이다. |
| 왜 틀렸나 | 과잉. Persistence라는 이름은 구매만 제한하지 않으며 이 클래스는 DB 확정 실패와 DB 취소 성공 뒤 Redis 복구 실패의 HTTP 계약을 함께 다룬다. 각 테스트는 이미 응답 계약 하나만 검증한다. 한 테스트를 새 클래스로 옮기는 것은 가능하지만 현재 이름이 잘못됐다고 단정할 근거는 부족하고, 필수 지적을 해결한 뒤 추가 구조 변경을 계속할 필요는 없다. |
| 어떻게 알았나 | 테스트와 설계 문서 대조. 08 문서의 확정과 취소는 모두 DB 기록 경계와 Redis 복구를 갖는다. JDK 21에서 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 --no-parallel ktlintCheck :order-service:test --tests '*RedisPersistenceResponseTest' --tests '*RedisCommitUnknownObservabilityTest'`로 HTTP 3개와 관측 2개 및 ktlint가 통과했다. 실패, 오류와 건너뜀은 모두 0이다. 당시 조립의 전체 common 66개와 order-service 296개도 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 --no-parallel ktlintCheck :common:test :order-service:test`로 통과했다. |
| 대신 한 것 | 필수 지적의 중복 저장 상태 단언만 제거하고 기존 클래스 구성을 유지한다. 후속 리팩터에서도 이 제안에 따른 클래스 분리는 추가하지 않았다. [PR #155](https://github.com/moonino/dropgate/pull/155). |
