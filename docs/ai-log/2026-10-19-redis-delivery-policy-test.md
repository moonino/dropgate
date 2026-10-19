# Redis 전달 정책을 한 개념으로 검증한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | reconnect 유지, replay 차단, 단절 중 명령 거절을 각각 다른 테스트로 나눈다. |
| 왜 틀렸나 | 과잉. 세 옵션은 재연결을 허용하면서 실행 여부가 불명확한 명령을 자동 반복하지 않는 전달 정책 하나를 구성한다. AGENTS.md는 한 개념이면 여러 단언을 허용한다. |
| 어떻게 알았나 | 문서 대조. AGENTS.md와 docs/design/02의 공통 클라이언트 경계를 [주문 리뷰](https://github.com/moonino/dropgate/pull/123#issuecomment-6260259387)와 [인증 리뷰](https://github.com/moonino/dropgate/pull/124#issuecomment-6260214384)에 대조했다. |
| 대신 한 것 | 실제 클라이언트 옵션은 함께 검증하고 Boot 타임아웃 보존과 응답 유실 뒤 Lua 중복 실행은 별도 테스트로 검증한다. PR #123 #124 #125. |
