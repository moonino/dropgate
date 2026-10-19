# 토큰 응답의 표현 변환을 컨트롤러에 둔다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | TokenPairResponse.createFrom 팩토리를 만들고 Bearer 상수와 만료 기간의 초 변환을 DTO로 옮긴다. |
| 왜 틀렸나 | 과잉. 버그가 없는 변환을 다시 옮기는 제안이다. #100은 응답 변환을 컨트롤러에 두도록 정했고 tokenType과 expiresIn의 표현 변환도 그 범위에 해당한다고 해석했다. |
| 어떻게 알았나 | 문서 대조. [공개 이슈 #100](https://github.com/moonino/dropgate/issues/100)의 응답 변환 경계와 [자동 리뷰 제안](https://github.com/moonino/dropgate/pull/119#issuecomment-6259646349)을 대조했다. 두 필드를 직접 열거한 지시가 아니라 컨트롤러 경계를 적용한 판단이다. |
| 대신 한 것 | 토큰 타입 상수와 기간 변환은 KakaoCallbackController에 유지했다. PR #119. |
