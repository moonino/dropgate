# 보상 경쟁의 DB 실패 코드는 시나리오 전제로 확인한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | [PR #154 제안](https://github.com/moonino/dropgate/pull/154#issuecomment-6262024935)은 지연된 DB 실패를 보상하는 경쟁 테스트에서 ORDER_PERSIST_FAILED 단언을 별도 테스트로 옮기고 완료만 기다리자는 내용이다. |
| 왜 틀렸나 | 테스트 불일치. 해당 오류는 별개의 응답 관측이 아니라 의도한 지연 구매가 DB 실패를 거쳐 보상됐다는 전제 확인이다. 완료만 기다리면 다른 실패가 우연히 같은 재고와 상태를 남겨도 통과할 수 있다. 보상 뒤 stock 1과 Redis 및 DB OPEN은 그 전제에서 확인하는 한 경쟁 결과다. |
| 어떻게 알았나 | 테스트와 설계 문서 대조. 02와 08의 다른 구매가 마지막 재고를 확정한 뒤 지연된 DB 실패를 보상하는 순서에 해당한다. 같은 소스를 포함한 당시 통합 스택에서 JDK 21로 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 --no-parallel ktlintCheck :common:test :order-service:test`로 common 66개와 order-service 294개 및 ktlint가 통과했다. 실패, 오류와 건너뜀은 모두 0이었다. main fbe3482f 재배치 뒤 변경 3개 파일의 바이트 동일성과 diff --check를 확인했으며 이 제안 때문에 테스트를 재실행하지 않았다. |
| 대신 한 것 | 시나리오의 오류 전제와 최종 재고 및 판매 상태를 함께 검증하는 head 85b4894를 유지한다. [PR #154](https://github.com/moonino/dropgate/pull/154). |
