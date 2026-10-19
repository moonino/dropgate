# 리프레시 만료 검증을 단순 삭제하지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 지적(인라인) |
| 제안 요지 | JwtTimestampValidator가 만료를 검사하므로 사후 require를 삭제한다. [리뷰](https://github.com/moonino/dropgate/pull/126#discussion_r4310604112) |
| 왜 틀렸나 | 테스트 불일치. 중복 지적은 타당하지만 단순 삭제는 exp와 now가 같은 순간을 허용한다. 기존 common과 auth 액세스 인증은 정확한 만료 시각부터 거절한다 |
| 어떻게 알았나 | 실행과 테스트. Spring Security 7.1.1 소스를 확인하고 고정 Clock 테스트를 추가했다. require만 지운 상태에서 `./gradlew :auth-service:test --tests dropgate.auth.KakaoLoginTest`의 62개 중 정확한 만료 경계 한 개가 실패했다 |
| 대신 한 것 | exp를 strict JwtClaimValidator 한 곳에 모으고 선택 nbf 검사를 보존했다. `./gradlew ktlintCheck :common:test :auth-service:test`의 common 60개와 auth 78개가 통과했다. 지적에는 반영으로 답하고 제안의 단순 삭제 부분만 쓰지 않았다. PR #126 |
