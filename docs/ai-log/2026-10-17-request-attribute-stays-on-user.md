# 요청 속성 키 REQUEST_ATTRIBUTE를 필터로 옮기지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-17, Claude Code 자동 리뷰 제안 |
| 제안 요지 | `AuthenticatedUser.REQUEST_ATTRIBUTE`는 HTTP 요청 속성 키라 웹 경계에 속하고 이 PR에서 쓰는 곳도 없으니, 이 상수를 쓰는 필터(6차)로 옮긴다. |
| 왜 틀렸나 | 과잉. 이 키는 필터 한곳의 값이 아니다. 요청에 사용자를 담는 `JwtAuthenticationFilter`와 꺼내 읽는 `AuthenticatedUserArgumentResolver`(#67), 관리자 확인을 하는 `AdminAuthorizationInterceptor`(#73)가 함께 쓰는 계약이다. 필터로 옮기면 읽는 두 쪽이 필터 클래스에 의존하게 된다. 또 값이 사용자 타입 이름(`AuthenticatedUser::class.java.name`)이라 타입 옆에 두는 편이 자연스럽다. 옮기려면 세 PR의 운영 코드와 테스트를 모두 고쳐야 해 이 PR 범위를 넘는다. 기각한 핵심 이유는 의존 방향이고 이는 일곱 원인 어디에도 정확히 맞지 않는다. 제안이 틀렸다기보다 스택 뒤 PR과 맞지 않아 쓰지 않은 것이며, 범위를 넘는 변경이라는 점에서 과잉이 가장 가깝다. |
| 어떻게 알았나 | 문서 대조. [리뷰 제안 3번](https://github.com/moonino/dropgate/pull/66#issuecomment-6230572013)의 기각 이유는 커밋 `refactor: 리뷰 제안을 반영한다 (#6)` 본문과 PR 본문 AI 절의 리뷰 반영 문단에 있다. 키를 필터, 리졸버, 관리자 인터셉터(#67, #73)가 공유하므로 옮기면 읽는 쪽이 필터 클래스에 의존한다는 내용이다. 사용처는 기록을 쓰며 2026-10-18에 아래 명령으로 다시 확인했다. `feat/6-bearer-filter`(#67)의 `JwtAuthenticationFilter.kt` 28행이 이 키로 사용자를 담고 `AuthenticatedUserArgumentResolver.kt` 20행이 읽으며, `feat/8-admin-authorization`(#73)의 `AdminAuthorizationInterceptor.kt` 11행도 읽는다. [두 번째 리뷰 제안 1번](https://github.com/moonino/dropgate/pull/66#issuecomment-6233088187)도 이 키를 이 PR에서 빼고 필터 쪽에 두자고 다시 냈다. |
| 대신 한 것 | `REQUEST_ATTRIBUTE`를 `AuthenticatedUser` 동반 객체에 그대로 두고 필터, 리졸버, 인터셉터가 이 상수 하나를 함께 쓴다. PR #66 |

```sh
git grep -n "REQUEST_ATTRIBUTE" feat/6-bearer-filter -- '*.kt'
git grep -n "REQUEST_ATTRIBUTE" feat/8-admin-authorization -- '*.kt'
```
