# 탐색 호출자의 재발급 표시 전달 테스트를 유지한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | session-navigation의 표시가 남아 있으면 만료로 이동하는 테스트를 순수 recovery 테스트와 중복이라 삭제한다. |
| 왜 틀렸나 | 과잉. 순수 함수는 전달받은 표시의 의미를 검증하고 탐색 테스트는 현재 URL의 표시를 그 함수까지 보존해 실제 assign에 넘기는 호출자 계약을 검증한다. 일반 tab과 hash 보존만 확인하면 refreshed만 선택적으로 누락하는 회귀를 잡지 못한다. |
| 어떻게 알았나 | 실행과 테스트. [후속 리뷰](https://github.com/moonino/dropgate/pull/147#issuecomment-6261556308)를 검증하려고 23490a6의 탐색 코드에 일시적으로 searchParams.delete("refreshed")만 넣었다. `npm test -- --maxWorkers=2 --reporter=verbose src/components/session-navigation.test.ts src/domain/session-recovery.test.ts`에서 순수 함수 3개와 일반 매핑 1개는 통과하고 표시 전달 테스트만 실패했다. 원본 바이트를 즉시 복원한 뒤 같은 명령은 5개 모두 통과했다. 원본과 복원본 SHA256이 같고 git status가 clean인 것도 확인했다. |
| 대신 한 것 | 표시 전달 테스트와 head 23490a6을 유지했다. 변이 코드는 커밋하거나 push하지 않았다. PR #147. |
