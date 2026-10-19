# 무효화 검증의 식별자 확인을 추가 분리하지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | verifyNotRevoked에서 jti 판독을 readTokenId로 분리하고 무효화 조회 함수에는 문자열만 넘긴다. |
| 왜 틀렸나 | 과잉. 현재 함수는 비어 있지 않은 jti로 무효화를 확인한다는 한 검증 경계이고 본문이 두 줄이다. 기존 제안에 따라 상위 verify에서 순서가 드러나게 이미 분리했다. 추가 분리는 새로운 불변식이나 외부 경계를 만들지 않고 짧은 검증의 읽는 지점만 늘린다. |
| 어떻게 알았나 | 코드 대조. [후속 리뷰 제안](https://github.com/moonino/dropgate/pull/132#issuecomment-6260786855)과 ed7dec1의 verify, verifyNotRevoked를 비교했다. 서명과 모든 클레임 판독 뒤 마지막에만 조회하는 순서는 CPU 2 제한의 common 66개 테스트로 유지됨을 확인했다. |
| 대신 한 것 | verifyNotRevoked의 두 줄을 유지하고 필수 지적이 없는 검증 head를 보존한다. PR #132. |
