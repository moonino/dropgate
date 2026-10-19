# 표시 소비 뒤 다음 만료를 복구하는 계약 테스트를 유지한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | ConsumeRefreshMarker 테스트에서 복귀 경로 계산을 없애고 표시가 지워졌는지만 검증한다. |
| 왜 틀렸나 | 과잉. 이 테스트의 한 개념은 성공 화면이 표시를 소비한 뒤 다음 세션 만료가 새 재발급으로 이어진다는 관찰 가능한 동작이다. 표시 삭제만 단언하도록 줄이면 소비한 URL과 실제 복구 경로 계산을 연결하는 계약을 검증하지 못한다. |
| 어떻게 알았나 | 테스트. [리뷰 제안](https://github.com/moonino/dropgate/pull/143#issuecomment-6261106034)을 9ba0902의 두 표시 소비 테스트와 대조했다. 첫 테스트는 query와 hash 보존, 두 번째는 다음 만료의 재발급 가능성을 각각 검증한다. 표시 판정 수정 뒤 `npm test -- --maxWorkers=2 src/server/request-session.test.ts src/server/protected-orders.test.ts src/components/ConsumeRefreshMarker.test.tsx src/domain/session-recovery.test.ts src/app/session/refresh/route.test.ts`의 40개가 통과했다. |
| 대신 한 것 | 다음 만료까지 연결하는 테스트를 유지했다. 값이 0인 표시는 소비하지 않는 새 실패 테스트를 추가하고 서버와 클라이언트 판정을 공통 isRefreshed 함수에 모았다. PR #143. |
