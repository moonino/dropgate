# 드롭 기본값 테스트를 컬럼별로 나누지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-17, Claude Code 자동 리뷰 제안 |
| 제안 요지 | SchemaConstraintTest의 `상태와 입장 속도를 주지 않은 드롭은 SCHEDULED와 50이 된다` 테스트를 status 기본값과 admission_rate_per_sec 기본값 두 테스트로 나누고 각 테스트가 컬럼 하나만 조회해 단언하게 한다. |
| 왜 틀렸나 | 과잉. 제안이 틀렸다기보다 나눌 필요가 없는 변경이다. 이 테스트가 고정하는 개념은 값을 주지 않은 드롭 행이 01-ERD의 DEFAULT대로 채워진다는 하나이고, 두 컬럼 값은 그 개념의 단언이라 한 테스트 한 개념 규칙에 이미 맞는다. 스택 위쪽 #71의 `상태와 시도 횟수를 주지 않은 outbox 이벤트는 PENDING과 0이 된다` 테스트도 같은 모양이라 이것만 나누면 기본값 테스트의 관례가 흐트러진다. |
| 어떻게 알았나 | 설계 문서 대조와 문서 대조. [리뷰 제안](https://github.com/moonino/dropgate/pull/70#issuecomment-6230571677)을 01-ERD drops 표와 맞춰 보니 status의 DEFAULT 'SCHEDULED'와 admission_rate_per_sec의 DEFAULT 50이 같은 표의 기본값 정의였다. feat/7-ledger-outbox-schema 브랜치의 SchemaConstraintTest에서 outbox 기본값 테스트가 status와 attempts를 한 행으로 조회해 한 번에 단언하는 것을 확인했다. |
| 대신 한 것 | 테스트를 나누지 않고 그대로 두었으며 리뷰 반영 커밋은 없다. PR #70 |
