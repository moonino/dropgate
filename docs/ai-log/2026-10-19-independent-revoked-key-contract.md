# 무효화 키 계약 테스트는 생산 코드 빌더를 재사용하지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | order 통합 테스트의 `auth:revoked:$tokenId` literal을 생산 코드의 createRevokedAccessTokenKey 호출로 바꾼다. |
| 왜 틀렸나 | 테스트 불일치. 이 테스트는 정본 Redis 키에 직접 저장한 직후 소비자가 거부하는 외부 키 계약을 확인한다. 생산 코드와 같은 빌더를 쓰면 그 빌더가 잘못된 앞머리를 만들어도 준비와 조회가 함께 바뀌어 계약 위반을 놓친다. 생산 코드의 키 생성은 common 한곳을 쓰되 계약의 기대값은 독립적으로 둔다. |
| 어떻게 알았나 | 설계 문서 대조. [리뷰 제안](https://github.com/moonino/dropgate/pull/132#issuecomment-6260729183)을 docs/design/02의 `auth:revoked:{jti}`와 비교했다. 최소 스텁 Red에서 지정 키를 SET해도 200이던 실패를 확인했고 실제 EXISTS 연결 뒤 같은 키의 401을 검증했다. |
| 대신 한 것 | 테스트의 literal을 유지한다. 생산자 auth의 로그아웃 키는 후속 auth 통합 PR에서 common 빌더를 재사용한다. PR #132. |
