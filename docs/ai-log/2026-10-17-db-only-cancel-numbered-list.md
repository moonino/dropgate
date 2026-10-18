# 08 4절 DB만 경로 문단을 번호 목록으로 나누지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-17, Claude Code 자동 리뷰 제안 |
| 제안 요지 | 08 4절 DB만 경로 문단을 Redis 경로처럼 1. 주문 행 FOR UPDATE 뒤 검사, 2. drops UPDATE, 3. orders 갱신의 번호 목록으로 나눈다. |
| 왜 틀렸나 | 설계 불일치. 코드가 설계와 다르다는 뜻이 아니라 제안이 설계 문서 08의 기존 형식과 내용에 맞지 않는다는 뜻이다. 같은 문서 2절의 1주 차 DB만 경로도 기본 목록의 번호를 참조해 차이만 적는 산문 문단이라, 4절만 번호 목록으로 바꾸면 한 문서 안에서 DB만 변형을 적는 형식이 갈린다. 제안한 세 단계는 4절 2번 트랜잭션에 든 order_ledger INSERT와 outbox INSERT, 커밋 뒤로 미룰 이유가 없다는 문장, 반대 순서면 동시 재구매와 교착한다는 이유 문장을 담지 못한다. 문단에는 고칠 사실 오류가 없다. |
| 어떻게 알았나 | 설계 문서 대조. [리뷰 제안 3번](https://github.com/moonino/dropgate/pull/82#issuecomment-6230604112)을 08 문서 2절과 4절에 대조했다. 2절 DB만 경로 문단은 "1주 차 DB만 경로: 4번이 ..."로 시작하는 산문이고, 4절 2번 단계는 orders 갱신과 order_ledger INSERT, outbox INSERT를 함께 적는다. 문단의 검사와 갱신 순서는 #83 브랜치 OrderCancelService.cancel의 순서(findForUpdate 뒤 검사, restoreOneUnitAndReopen, order.cancel)와 같았다. 코드 읽기로 확인했고 실행하지 않았다. |
| 대신 한 것 | 4절 DB만 경로 문단을 산문으로 두고 같은 리뷰의 나머지 제안 두 건만 반영했다(커밋 `docs: 리뷰 제안을 반영한다 (#9)`). PR #82 |
