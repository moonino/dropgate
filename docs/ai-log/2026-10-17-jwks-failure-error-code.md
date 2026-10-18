# JWKS 조회 실패의 오류 코드를 fetcher 예외에서 이어받지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-17, Claude Code 자동 리뷰 제안 |
| 제안 요지 | `FetchFailure.keys()`(반영 뒤 `resolveKeys()`)가 이미 AUTH_UNAVAILABLE인 예외를 같은 코드로 다시 감싸므로 원래 예외의 `errorCode`를 이어받아 오류 코드를 한 곳에서만 정한다. |
| 왜 틀렸나 | 설계 불일치. 제안을 적용해도 지금 동작은 바뀌지 않는다. 지금 fetcher인 `RestClientJwkSetFetcher`도 AUTH_UNAVAILABLE을 던지기 때문이다. 다만 오류 코드를 정하는 곳이 캐시에서 fetcher 구현으로 옮겨 가므로 가장 가까운 원인으로 설계 불일치를 골랐다. ADR 0018과 05 문서 오류 코드표, 08 문서 1절은 JWKS를 받지 못하거나 형식이 깨지면 503 AUTH_UNAVAILABLE이라고 정했고 조회를 누가 하는지는 다루지 않는다. `JwkSetFetcher`는 문자열만 돌려주는 인터페이스이고 어떤 코드로 실패할지 정하지 않으므로, 이 결정을 지키는 경계는 캐시다. 코드를 이어받으면 다른 코드를 던지는 fetcher가 생기는 순간 응답이 ADR과 어긋난다. |
| 어떻게 알았나 | 설계 문서 대조. [리뷰 제안 2번](https://github.com/moonino/dropgate/pull/65#issuecomment-6230578074)을 feat/6-jwks-cache 브랜치의 ADR 0018 결정 절, docs/design/05-API-명세.md 오류 코드표, docs/design/08-시퀀스-흐름.md 1절과 맞대 봤다. 세 문서 모두 JWKS 조회 실패, 연결 3초나 읽기 3초 초과, 형식 오류를 AUTH_UNAVAILABLE로 정했고 fetcher 구현에 따라 코드를 나누지 않는다. 제안을 적용해 실행해 보지는 않았다. |
| 대신 한 것 | 원래 예외를 cause로 담고 캐시의 `FetchFailure.resolveKeys()`에서 AUTH_UNAVAILABLE을 정하는 구조를 유지한다. PR #65 |
