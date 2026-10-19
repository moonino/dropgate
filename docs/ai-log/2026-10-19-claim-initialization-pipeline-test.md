# 초기화 권한과 Redis 키 생성은 하나의 파이프라인으로 검증한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | 스케줄러의 정상 초기화 테스트를 claim 영속화와 Redis 키 생성 테스트로 나눈다. [리뷰](https://github.com/moonino/dropgate/pull/129#issuecomment-6260525991) |
| 왜 틀렸나 | 과잉. 정상 초기화는 DB 전이 뒤 권한을 커밋하고 해당 권한으로 Redis를 초기화하는 하나의 통합 계약이다. claim과 키 생성 단언을 별도 테스트로 나누면 같은 파이프라인을 중복 실행한다. |
| 어떻게 알았나 | 설계 문서 02와 ADR 0007을 대조했다. 초기화 권한을 Redis 호출 전에 영속화해야 하므로 양쪽 결과를 함께 확인해야 연결 누락을 잡는다. Java 21에서 `./gradlew ktlintCheck :common:test :order-service:test`로 common 60개와 order-service 172개가 통과했다. |
| 대신 한 것 | 중복 OPEN 단언과 별개 개념을 섞은 실패 테스트는 제안대로 정리했다. 정상 초기화는 claim과 키를 함께 검증한다. PR #129 |
