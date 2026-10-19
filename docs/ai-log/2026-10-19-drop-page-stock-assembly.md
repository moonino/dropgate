# 짧은 페이지 조립의 재고 부착을 유지한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | DropService.toPage의 재고 부착을 별도 함수로 분리한다. |
| 왜 틀렸나 | 과잉. 짧은 본문이 응답 항목과 커서를 정하고 그 항목의 재고를 한 번 읽어 같은 서비스 페이지를 조립한다. 부착을 분리해도 새로운 경계가 생기지 않는다. |
| 어떻게 알았나 | 테스트. [리뷰 제안](https://github.com/moonino/dropgate/pull/141#issuecomment-6261057359)을 DropService.toPage와 대조했다. 실제 명령 리스너로 커서용 추가 행 제외와 MGET 한 번을 확인했고, JDK 21의 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 ktlintCheck :order-service:test`에서 order 202개가 통과했다. |
| 대신 한 것 | 조립을 유지하고 수량 숫자와 구별되도록 변수 이름은 stocksById로 고쳤다. PR #141. |
