# Redis 초기화 준비를 전체 HTTP 기준선으로 올리지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | [PR #155 제안](https://github.com/moonino/dropgate/pull/155#issuecomment-6262048006)은 HTTP 테스트의 initializeStock과 재고 조회를 공통 OrderApiIntegrationTest로 옮겨 생성자 주입과 중복을 줄이자는 내용이다. |
| 왜 틀렸나 | 과잉. HTTP 응답 테스트와 수동 Flow 테스트는 서로 다른 조립 경계를 사용한다. DB 경로의 공통 HTTP 기준선까지 Redis Lua 준비 책임을 늘릴 필요는 없다. 필수 지적을 반영하면서 HTTP의 재고 조회 단언도 제거했으므로 해당 조회 중복은 이미 없어졌다. PR #155 당시에는 남은 초기화 도우미를 해당 장애 테스트 한 클래스에 두었다. 후속 활성화 뒤에는 DB 공통 기준선을 늘리는 대신 Redis 전용 HTTP 기준선을 재사용했다. |
| 어떻게 알았나 | 테스트. 당시 HTTP 클래스의 실제 세 사용처와 생성자 주입을 대조했다. JDK 21에서 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 --no-parallel ktlintCheck :order-service:test --tests '*RedisPersistenceResponseTest' --tests '*RedisCommitUnknownObservabilityTest'`로 HTTP 3개와 관측 2개 및 ktlint가 통과했다. 실패, 오류와 건너뜀은 모두 0이다. DB 기준선인 OrderApiIntegrationTest의 책임까지 늘리지 않는다는 판단은 유지했다. |
| 대신 한 것 | [PR #155](https://github.com/moonino/dropgate/pull/155)에서는 응답 계약만 남기고 private initializeStock과 redisPurchaseFlow 이름을 사용했다. 이후 [PR #157](https://github.com/moonino/dropgate/pull/157)에서 실제 Redis 전용 기준선으로 세 테스트 클래스를 옮겨 중복 초기화와 수동 조립을 없앴다. DB 공통 기준선으로 책임을 올리자는 원래 제안은 쓰지 않았다. |
