# 인증 토큰의 검증 결과와 무효화 확인 순서를 분리하지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 지적(인라인) |
| 제안 요지 | 기존 Clock 경계와 만료 및 리프레시 거절 테스트의 조회 횟수 단언을 떼어 별도 테스트 세 개로 나눈다. |
| 왜 틀렸나 | 과잉. 각 테스트는 해당 토큰 상태에서 무효화 확인 뒤 통과하거나 확인 전에 거부한다는 순서 계약 한 개념을 검증한다. 거부만 확인하면 Redis를 먼저 읽은 구현을 놓치고 조회 0회만 확인하면 인증 거부를 놓친다. 정상 경로도 결과와 조회 1회가 함께 있어야 확인 뒤 통과를 고정한다. AGENTS.md는 같은 개념의 여러 단언을 허용한다. |
| 어떻게 알았나 | 테스트. [필수 지적](https://github.com/moonino/dropgate/pull/137#discussion_r4310812088)을 AGENTS.md와 08 문서의 서명과 클레임 뒤 무효화 확인 순서에 대조했다. 최소 주입 Red에서는 정상 조회 미호출과 실패 미전달, 로그아웃 뒤 두 경로가 실패했고 마지막 조회를 연결한 전체 auth 91개는 JDK 21의 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 ktlintCheck :common:test :auth-service:test :order-service:test`로 통과했다. |
| 대신 한 것 | 이름을 만료 직전 확인 뒤 통과, 만료 시각부터 확인 전 거부, 리프레시 확인 전 거부로 명확히 하고 기존 단언을 유지했다. JDK 21의 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 :auth-service:ktlintCheck :auth-service:test --tests 'dropgate.auth.AccessTokenDecoderTest'`로 단위 6개를 재검증했다. PR #137. |
