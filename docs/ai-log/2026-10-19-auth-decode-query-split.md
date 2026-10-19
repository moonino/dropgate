# 인증 디코더의 기존 예외 경계를 추가 분리하지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | 서명과 클레임 검증을 private 함수로 빼고 try 바깥의 decode에서 무효화 조회를 호출한다. |
| 왜 틀렸나 | 과잉. 분리 자체는 가능한 가독성 개선이지만 이번 변경의 외부 호출은 이미 공통 인터페이스와 Redis 래퍼를 통해 한 줄로 수행한다. 기존 try는 JwtException과 IllegalArgumentException만 바꾸므로 AUTH_UNAVAILABLE은 그대로 전달된다. 기존 디코더 본문까지 추가 분리해도 현재 계약의 결함을 해결하는 변화는 없고 검증 범위만 넓어진다. |
| 어떻게 알았나 | 테스트. [리뷰 제안](https://github.com/moonino/dropgate/pull/137#issuecomment-6260878371)을 실제 catch 타입과 DropgateException의 RuntimeException 상속에 비교했다. JDK 21의 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 ktlintCheck :common:test :auth-service:test :order-service:test`로 가짜가 AUTH_UNAVAILABLE을 던지는 테스트와 전체 auth 91개가 통과해 실패 코드가 보존됨을 확인했다. 리뷰의 버그 검사도 같은 전달을 확인했다. |
| 대신 한 것 | 현재의 마지막 단계 조회와 예외 경계를 유지하고 검증 순서와 실패 보존 테스트를 둔다. PR #137. |
