# 토큰 응답의 표현 변환을 컨트롤러에 둔다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | TokenPairResponse.createFrom 팩토리를 만들고 Bearer 상수와 만료 기간의 초 변환을 DTO로 옮긴다. |
| 왜 틀렸나 | 과잉. 버그가 없는 변환을 다시 옮기는 제안이다. #100 작업 범위는 tokenType과 expiresIn 변환을 컨트롤러에서 하도록 명시했다. |
| 어떻게 알았나 | 문서 대조. #100의 작업 범위와 을 대조했고, 컨트롤러의 표현 변환은 요청한 경계를 지킨다. [자동 리뷰 제안](https://github.com/moonino/dropgate/pull/119#issuecomment-6259646349). |
| 대신 한 것 | 토큰 타입 상수와 기간 변환은 KakaoCallbackController에 유지했다. PR #119. |
