# 판매 상태 갱신 가드의 위치를 유지한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | JPQL의 OPEN/SOLD_OUT 조건을 서비스의 조기 반환으로 옮기고 UPDATE를 단순화한다. |
| 왜 틀렸나 | 과잉. 현재 조건은 updateSaleStatusIfSelling 한곳에만 있으며 함수 이름으로 갱신 범위가 드러난다. 잠금 뒤 새 스냅샷과 종료 상태 보존 계약은 이미 충족한다. 조기 반환은 SCHEDULED/CLOSED의 주문 수 조회와 초과 확정 예외까지 생략하므로 현재 동작을 그대로 두는 정리는 아니다. 이번 상태 동기화 범위에서 이 관측 변화가 필요한 근거는 없다. |
| 어떻게 알았나 | 테스트와 설계 문서 대조. [PR #149 제안](https://github.com/moonino/dropgate/pull/149#issuecomment-6261520177)을 행 잠금 뒤 별도 문장으로 확정 수를 읽는 서비스와 비교했다. 총수량 초과면 상태와 무관하게 예외를 내며 마지막 JPQL만 OPEN/SOLD_OUT을 제한한다. main 949b7816 기준 JDK 21에서 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 ktlintCheck :common:test :order-service:test`로 common 66개와 order 240개가 통과했다. 실제 잠금 뒤 새 스냅샷과 SCHEDULED/CLOSED 보존 테스트도 포함한다. |
| 대신 한 것 | 검증된 source head ec6b770을 유지하고 제안의 범위 보류 근거만 남긴다. PR #149. |
