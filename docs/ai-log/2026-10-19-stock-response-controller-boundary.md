# 재고 모름을 HTTP 컨트롤러에서 null로 바꾼다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안 |
| 제안 요지 | RemainingStock 변환을 응답 DTO의 팩토리로 옮긴다. |
| 왜 틀렸나 | 과잉. DTO 팩토리도 HTTP 경계라 가능한 정리지만 114 작업 범위는 컨트롤러가 명시적 값을 null로 바꾸도록 정했다. 현재 변환은 이 경계를 지킨다. |
| 어떻게 알았나 | 문서 대조. [리뷰 제안](https://github.com/moonino/dropgate/pull/141#issuecomment-6261057359)을 114 실행 범위와 controller의 변환 위치에 대조했다. `./gradlew ktlintCheck :order-service:test`의 order 202개가 통과했다. [리뷰](https://github.com/moonino/dropgate/pull/141#issuecomment-6261057359). |
| 대신 한 것 | 변환 위치를 유지하고 value는 dropWithStock으로 명확히 했다. PR #141. |
