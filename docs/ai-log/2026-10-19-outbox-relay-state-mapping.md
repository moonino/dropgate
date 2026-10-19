# outbox 상태 매핑을 후속 기능으로 다시 나누지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안 |
| 제안 요지 | 배치 발행 PR에서 아직 바꾸지 않는 attempts와 FAILED 상태 매핑은 실패 정책을 구현하는 후속 PR로 옮긴다. |
| 왜 틀렸나 | 과잉. 기존 DB 스키마가 PENDING, PUBLISHED, FAILED와 attempts를 이미 정의하며 바로 다음 기능이 같은 매핑을 사용한다. 이를 임시로 제외했다가 다시 더하면 동작 이득 없이 ORM 매핑 변경만 반복된다. |
| 어떻게 알았나 | 설계 문서 대조와 문서 대조. [PR #131의 제안 2번](https://github.com/moonino/dropgate/pull/131#issuecomment-6260549554)을 docs/design/01-ERD.md outbox 정의, V2__create_ledger_and_outbox.sql과 OutboxEntity의 상태 매핑 및 후속 실패 정책 구현에 대조했다. 기존 스키마와 같은 상태 표현이며 새로운 상태를 미리 만든 변경이 아니다. |
| 대신 한 것 | 기존 스키마와 일치하는 상태와 attempts 매핑을 유지하고 후속 일반 실패 정책에서 사용한다. PR #131 |
