# 공개 엔드포인트 테스트의 status 200 단언을 지우지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-17, Claude Code 자동 리뷰 제안 |
| 제안 요지 | `MockHttpServletResponse`의 기본 상태가 200이라 `공개 엔드포인트는 토큰 없이 통과한다` 테스트의 `assertThat(response.status).isEqualTo(200)`은 필터 동작과 상관없이 늘 통과하므로 지우고 `chain.request`만 검증한다. |
| 왜 틀렸나 | 테스트 불일치. 이 단언이 검증을 하지 않는다는 전제가 틀렸다. 필터가 공개 경로를 잘못 막으면 401을 써서 이 단언이 실패하므로 필터 동작과 무관하지 않다. 오류 응답을 쓰고도 체인을 부르는 결함(return 누락)이 겹치면 `chain.request` 단언은 통과하고 이 단언만 실패한다. 두 단언은 요청을 통과시킨다는 한 개념의 양면이다. |
| 어떻게 알았나 | 문서 대조. [리뷰 제안 2번](https://github.com/moonino/dropgate/pull/67#issuecomment-6230573883)을 feat/6-bearer-filter의 JwtAuthenticationFilter(실패하면 오류 응답을 쓰고 return)와 JwtAuthenticationFilterTest에 대조했다. 실행으로 확인하지는 않았다. 다음 차례에 같은 제안이 [다시 올라왔고](https://github.com/moonino/dropgate/pull/67#issuecomment-6233114347), 2026-10-18 [자동 리뷰 요약](https://github.com/moonino/dropgate/pull/67#issuecomment-6249089712)은 같은 근거로 이 제안을 올리지 않았다. |
| 대신 한 것 | 단언을 그대로 두고 기각 이유를 커밋 `test: 리뷰 제안을 반영한다 (#6)` 본문과 PR 본문에 적었다. PR #67 |
