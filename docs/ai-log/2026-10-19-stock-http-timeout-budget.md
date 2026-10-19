# HTTP의 장애 응답 시간도 확인한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안 |
| 제안 요지 | reader에서 시간을 검증하므로 HTTP 장애 테스트의 시간 단언과 출력을 없앤다. |
| 왜 틀렸나 | 과잉. reader의 명령 제한과 전체 HTTP 경계는 다르다. HTTP까지 연결했을 때 추가 반복이나 지연이 없는지도 확인하며 요청된 200ms 근처 응답 계약의 근거를 남긴다. |
| 어떻게 알았나 | 테스트. `./gradlew ktlintCheck :order-service:test`의 초기 통합 검증 202개가 통과했다. 합성 장애 시험에서 별도 Redis를 정지한 HTTP 단건과 목록이 각각 약211ms와217ms에 200과 null로 완료됐다. [리뷰](https://github.com/moonino/dropgate/pull/141#issuecomment-6261126828). |
| 대신 한 것 | 관측 시간과 200/null 응답 검증을 유지한다. 부하 성능이나 정밀한 지연 상한으로 해석하지 않는다. PR #141. |
