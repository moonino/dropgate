# 보호 guard의 재발급 표시 전달 테스트를 유지한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | request-session.test.ts에서 표시별 재발급과 만료 분기를 없애고 순수 복구 경로 테스트에만 남긴다. |
| 왜 틀렸나 | 과잉. 순수 복구 함수의 테스트는 guard가 잘못된 context를 전달하거나 표시를 버리는 회귀를 잡지 못한다. 인증 서버의 401과 액세스 쿠키 부재가 보호 guard에서 올바른 경로로 이어지는지는 호출자 경계에서 검증해야 한다. |
| 어떻게 알았나 | 테스트. [리뷰 제안](https://github.com/moonino/dropgate/pull/143#issuecomment-6261106034)을 9ba0902의 requireViewer 및 protected-orders 테스트와 대조했다. 전자는 인증 guard의 표시 전달, 후자는 인증 성공 뒤 주문 조회의 401이라는 별도 실패 경로다. 표시 판정 수정 뒤 `npm test -- --maxWorkers=2 src/server/request-session.test.ts src/server/protected-orders.test.ts src/components/ConsumeRefreshMarker.test.tsx src/domain/session-recovery.test.ts src/app/session/refresh/route.test.ts`의 40개가 통과했다. |
| 대신 한 것 | caller 경계 테스트를 유지하고 판정 구현만 공통 isRefreshed 함수에 모았다. PR #143. |
