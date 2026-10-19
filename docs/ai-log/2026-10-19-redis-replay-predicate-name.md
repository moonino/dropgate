# 한 곳의 replay 제외 조건을 유지한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | replayFilter의 true를 excludeAllFromReplay 같은 이름 있는 값으로 뺀다. |
| 왜 틀렸나 | 과잉. 상수 조건은 Boot 빌더 경계 한 곳에 있고 전체 명령 제외 정책이다. 별도 일회성 Predicate 선언을 추가하지 않았다. |
| 어떻게 알았나 | 실행과 테스트. 을 Lettuce 7.5.2 구현과 대조했다. 실제 응답 유실 테스트가 기본 설정에서 실패하고 프로덕션 설정에서 통과했다. [리뷰 제안](https://github.com/moonino/dropgate/pull/123#issuecomment-6260205626). |
| 대신 한 것 | 한 곳의 필터와 실제 클라이언트 정책 검증을 유지하고 TCP 회귀 테스트를 추가한다. PR #123 #125. |
