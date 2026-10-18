# 예상 밖 응답의 code 읽기를 409 분기 안으로 옮기지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-17, Claude Code 자동 리뷰 제안 |
| 제안 요지 | `load/k6/purchase.js`에서 `response.status === CONFLICT_STATUS`를 두 번 검사하는 두 `if`를 409 블록 하나로 묶고, `readErrorCode`도 409일 때만 부르게 한다. |
| 왜 틀렸나 | 설계 불일치. 부분 기각이다. 409 블록 하나로 묶는 앞 절반은 반영했고, `readErrorCode`를 409에서만 부르는 뒤 절반만 쓰지 않았다. 제안이 설계와 다른 코드를 낸 것은 아니어서 딱 맞는 원인은 없고, 설계가 응답을 code로 가르게 정했는데 그 구분을 집계에서 잃게 된다는 점이 가장 가깝다. docs/design/05-API-명세.md는 오류 응답을 `{code, message, errors[]}` 하나로 두고 클라이언트가 code로 분기한다고 정했고, openapi.yaml의 구매 엔드포인트는 409 말고도 400, 401, 403, 404, 503을 모두 code가 필수인 ErrorResponse로 돌려준다. code를 409일 때만 읽으면 이런 응답이 `purchase_unexpected`에 status 태그만 남기고 code 태그를 잃는다. 설계상 구매의 503은 AUTH_UNAVAILABLE, STOCK_UNAVAILABLE, ORDER_PERSIST_FAILED 셋이라 상태만으로 원인을 가를 수 없다. 다만 이 브랜치 코드에는 STOCK_UNAVAILABLE과 ORDER_PERSIST_FAILED를 던지는 곳이 아직 없어, 지금 DB만 경로에서 구매가 낼 수 있는 503은 AUTH_UNAVAILABLE뿐이다. |
| 어떻게 알았나 | 설계 문서 대조. [리뷰 제안 1번](https://github.com/moonino/dropgate/pull/87#issuecomment-6230616528)을 `perf/10-db-only-load-tools` 브랜치의 `recordOutcome`과 대조했다. 예상 밖 응답은 `unexpectedResponses.add(1, { status, code })`로 code 태그를 남기고, 그 code는 201 분기 뒤에서 읽은 값이다. 기각 근거는 커밋 본문의 "401, 503 같은 예상 밖 응답에서도 code 태그가 남아야 원인을 바로 볼 수 있다"이고, 05-API-명세.md 오류 코드표와 openapi.yaml의 구매 응답 정의를 대조해 409가 아닌 응답도 code를 싣는다는 것을 확인했다. 실행이나 측정으로 확인한 것은 아니다. |
| 대신 한 것 | 409 검사는 블록 하나로 묶고 `readErrorCode` 호출은 201 분기 뒤 지금 자리에 둔다(커밋 `refactor: 리뷰 제안대로 409 분기를 묶는다`). PR #87 |
