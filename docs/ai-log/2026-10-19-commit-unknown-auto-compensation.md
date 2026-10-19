# 커밋 결과를 모르는 DB 예외를 자동 보상하지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Codex 코딩 초안 |
| 제안 요지 | Redis 예약 뒤 DB 확정 호출이 RuntimeException을 던지면 항상 compensate.lua로 재고를 되돌리는 초안이었다. 원본은 개인 공간 work/w2/drafts/RedisPurchaseFlow.kt에 남겼다. 정책 출처는 당시 #113 프롬프트의 DB 예외 보상 지시와 ADR 0007 및 08 시퀀스의 같은 정책이다. 자동 리뷰 지적 사례가 아니다. |
| 왜 틀렸나 | 동시성 가정 오류. 호출자가 예외를 받았다는 사실만으로 DB 롤백을 확정할 수 없다. PostgreSQL COMMIT 뒤 콜백이 실패해도 CONFIRMED 주문은 남는다. 이때 재고를 늘리고 다른 사용자가 사면 총수량 1에 확정 주문 2건이 생긴다. 롤백 실패가 전용 예외를 감싼 경우에도 원인 체인을 보고 보상하면 안 된다. |
| 어떻게 알았나 | 테스트. 원본 초안의 무조건 보상 정책을 재현한 변이를 실제 PostgreSQL과 Redis로 실행했다. 원본 파일 그대로의 실행이라고 주장하지 않는다. JDK 21에서 실행한 명령은 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 --no-parallel :order-service:test --tests '*RedisCommitUnknownTest'`다. 보관 결과 work/w2/drafts/redis-commit-unknown-red.xml의 테스트 2개가 모두 실패했다. afterCommit 장애 뒤 두 번째 구매까지 실행한 결과 CONFIRMED 기대 1건, 실제 2건이었다. 롤백 실패 래퍼 사례는 stock 기대 0, 실제 1이었다. 수정 후 첫 통합 명령 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 --no-parallel ktlintCheck :common:test :order-service:test`에서 common 66개, order-service 294개와 ktlint가 통과했고 실패, 오류와 건너뜀은 모두 0이었다. 당시 Green 2개는 work/w2/drafts/redis-commit-unknown-green.xml에 별도 보관했다. 당시 보관한 Green은 CONFIRMED 1건, stock 0, 최초 구매자만 buyers에 유지, unknown 카운터 1을 검증한다. |
| 대신 한 것 | 설계를 먼저 [ADR 0021](https://github.com/moonino/dropgate/blob/main/docs/adr/0021-db-커밋이-불확실하면-재고를-복구하지-않는다.md)로 고쳤다. 본문 flush 실패를 전용 예외로 구분하고, 롤백이 성공해 최상위로 그대로 전파된 전용 예외만 보상한다. 그 밖의 커밋 또는 롤백 불확실은 예약 유지와 503 및 별도 관측으로 처리한다. 경계 구현은 [PR #151](https://github.com/moonino/dropgate/pull/151), Flow와 재판매 안전성 회귀 테스트는 [PR #153](https://github.com/moonino/dropgate/pull/153)에 포함했고, 카운터와 판매 상태의 관측 검증은 [PR #155](https://github.com/moonino/dropgate/pull/155)의 독립 테스트로 분리했다. |
