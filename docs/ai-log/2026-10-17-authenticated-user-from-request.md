# 요청에서 인증 사용자를 꺼내는 처리를 AuthenticatedUser companion으로 합치지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-17, Claude Code 자동 리뷰 제안 |
| 제안 요지 | AdminAuthorizationInterceptor와 AuthenticatedUserArgumentResolver가 요청 속성에서 `AuthenticatedUser`를 꺼내고 없으면 `UNAUTHENTICATED`를 던지는 처리를 `AuthenticatedUser` companion의 `fromRequest` 같은 함수 하나로 모은다. |
| 왜 틀렸나 | 과잉. 제안이 동작을 틀리게 만들지는 않지만 필요 없는 공통화다. 바뀌면 함께 고쳐야 하는 속성 키는 `AuthenticatedUser.REQUEST_ATTRIBUTE`, 오류 코드는 `ErrorCode.UNAUTHENTICATED`로 이미 한곳에 있다. 남은 두 곳은 받는 요청 타입(`HttpServletRequest`와 `NativeWebRequest`)과 오류 메시지 맥락(요청 URI와 핸들러 메서드 이름)이 다르다. 값 객체인 `AuthenticatedUser`에 요청 해석을 넣으면 바뀔 이유가 둘이 된다. |
| 어떻게 알았나 | 문서 대조. [리뷰 제안](https://github.com/moonino/dropgate/pull/73#issuecomment-6230618298)을 `feat/8-admin-authorization`의 AdminAuthorizationInterceptor.kt 11행, 12행과 AuthenticatedUserArgumentResolver.kt 20행, 21행, AuthenticatedUser.kt에 대조했다. 두 곳이 같은 상수 키와 오류 코드를 쓰고, 요청 타입과 오류 메시지만 서로 다름을 확인했다. 같은 날 다음 차례 [리뷰 제안](https://github.com/moonino/dropgate/pull/73#issuecomment-6233128891)도 `AuthenticatedUser.from(request: HttpServletRequest)`로 모으자는 같은 취지였다. |
| 대신 한 것 | 코드를 바꾸지 않고 두 곳이 `AuthenticatedUser.REQUEST_ATTRIBUTE`와 `ErrorCode.UNAUTHENTICATED`를 함께 쓰는 지금 구조를 유지했다. 기각 이유는 PR 본문 리뷰 반영 절에 적었다. PR #73 |
