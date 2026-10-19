# 테스트 토큰의 식별자 전달과 클레임 변형을 함께 유지한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | issueToken의 tokenId 파라미터를 없애고 issueAccessToken이 customize 안에서 jwtID를 덮어쓰게 한다. |
| 왜 틀렸나 | 과잉. 제안은 issueAccessToken의 공개 식별자 입력을 유지하면서 내부 jti 기본값을 설정하는 위치만 옮긴다. 명확화로서 타당하지만 현재 fixture는 일반 customize가 기본값 뒤에서 잘못된 클레임을 의도적으로 덮어쓰는 순서가 명시돼 있고 iss, sub, exp도 같은 방식을 쓴다. 동작 변경 없는 내부 정리는 이번 연결 범위에 더하지 않았다. |
| 어떻게 알았나 | 코드와 실행 대조. 을 TestTokenIssuer와 필수 클레임 변형 테스트에 대조했다. 실제 order 테스트는 문자열 tokenId만으로 토큰을 발급하며, Nimbus 직접 참조 없이 컴파일하고 직접 Redis SET 직후 401을 확인했다. [후속 리뷰 제안](https://github.com/moonino/dropgate/pull/132#issuecomment-6260786855). |
| 대신 한 것 | 정상 식별자 전달과 일반 클레임 변형의 용도를 유지한다. PR #132. |
