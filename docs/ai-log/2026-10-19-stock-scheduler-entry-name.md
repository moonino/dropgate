# 스케줄러 진입점의 기존 전이 이름을 유지한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | 상태 전이와 초기화를 함께 수행하는 스케줄러 진입점 transitionDueDrops를 tick 등으로 바꾼다. |
| 왜 틀렸나 | 과잉. 기존 호출자가 사용하는 상태 전이 진입점을 일반적인 tick으로 바꾸면 드롭 전이라는 의미가 약해진다. 짧은 본문이 전이 후 초기화 순서를 보여 주고 초기화는 별도 함수로 드러나 있다. |
| 어떻게 알았나 | 기존 스케줄러와 테스트 호출을 대조했다. 진입점은 transitions.transitionDueDrops 뒤 initializeClaimedStocks를 호출하는 본문이며 후자의 이름이 추가 책임을 명시한다. JDK 21에서 `./gradlew ktlintCheck :common:test :order-service:test`의 common 60개와 order-service 172개가 통과했다. [리뷰](https://github.com/moonino/dropgate/pull/129#issuecomment-6260595988). |
| 대신 한 것 | initializeClaimedStocks 추출은 채택했고 기존 transitionDueDrops 진입점은 유지했다. PR #129. |
