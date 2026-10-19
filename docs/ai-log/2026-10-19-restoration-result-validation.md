# 보상과 취소의 성공 반환 범위를 각각 확인한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | 보상과 취소 래퍼의 실패 판정을 -1 비교 하나로 통일한다. |
| 왜 틀렸나 | 과잉. 현행 Lua가 정한 값만 반환하는 동안 -1 비교로 바꾸어도 결과가 같으므로 가능한 단순화다. 다만 보상은 복구 수량 1 이상이고 취소는 상태 코드 0 또는 1이라는 서로 다른 성공 의미를 이름과 범위로 드러내는 현재 경계를 유지한다. 현재 검사는 예상 밖의 반환도 거절하며 이번 변경에서 이 방어 범위를 줄일 필요는 없다. |
| 어떻게 알았나 | 설계 문서 대조, 테스트. [리뷰 제안](https://github.com/moonino/dropgate/pull/144#issuecomment-6261151270)을 02 문서와 compensate.lua, cancel.lua의 실제 반환 및 래퍼 조건에 비교했다. JDK 21의 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 ktlintCheck :common:test :order-service:test`에서 common 66개와 order 220개가 통과했다. |
| 대신 한 것 | 복구 수량과 판매 상태 코드의 성공 범위를 각 래퍼에서 확인한다. PR #144. |
