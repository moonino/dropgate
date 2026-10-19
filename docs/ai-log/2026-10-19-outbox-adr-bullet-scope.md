# ADR 항목을 나누는 문장 정리는 미룬다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | ADR 0008에서 attempts와 occurred_at 규칙을 다른 항목으로 나눈다. |
| 왜 틀렸나 | 과잉. 항목 분리는 정리로서 타당하지만 발행 실패 횟수와 사건 시각의 계약은 04 문서 및 ADR과 일치한다. 의미를 바꾸지 않는 추가 편집은 이번 필수 오류 수정 범위에서 미룬다. |
| 어떻게 알았나 | 문서 대조. Redis 연결 실패 제외, 10회째 FAILED, confirmedAt 또는 canceledAt 사용 규칙이 서로 같다. [리뷰](https://github.com/moonino/dropgate/pull/120#issuecomment-6259776284). |
| 대신 한 것 | ADR 문장 배치는 유지하고 필수 지적만 반영했다. PR #120. |
