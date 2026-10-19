# 확정 기록 도우미의 이름은 클래스 맥락으로 구분한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | [PR #157 제안](https://github.com/moonino/dropgate/pull/157#issuecomment-6262329296)은 OrderConfirmationRecorder.recordConfirmed가 OrderHistoryRecorder.recordConfirmed와 같아 혼동될 수 있으므로 ConfirmedOrderWriter.insertConfirmedOrder 등으로 이름을 바꾸자는 내용이다. |
| 왜 틀렸나 | 과잉. 현재 클래스 이름이 주문 확정 기록이라는 맥락을 드러내고, 내부 이력 기록은 history라는 의존성 이름으로 구분한다. 도우미는 주문 생성과 INSERT 0행 판정뿐 아니라 원장과 outbox 기록도 함께 묶는다. insert를 중심으로 좁힌 이름도 이 전체 역할을 더 명확히 한다고 단정하기 어렵다. 가능한 명명 선택이지만 현재 기능이나 경계를 고칠 필요는 없다. |
| 어떻게 알았나 | 테스트와 설계 문서 대조. 소유 서비스의 트랜잭션 안에서 주문, 원장과 outbox를 확정하는 08 및 ADR 0021의 경계와 실제 세 기록 호출을 대조했다. 동일 생산 소스를 포함한 초기 순수 리팩터에서 JDK 21로 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 --no-parallel ktlintCheck :common:test :order-service:test`로 common 66개와 order-service 294개 및 ktlint가 통과했다. 실패, 오류와 건너뜀은 모두 0이었다. 이후 마지막 테스트 정리도 이 생산 소스를 바꾸지 않았다. |
| 대신 한 것 | 클래스 맥락으로 구분한 기존 이름을 유지하고 마지막 배치는 테스트 조립과 정확한 테스트 범위 명명만 정리한다. [PR #157](https://github.com/moonino/dropgate/pull/157). |
