# 목록 쿼리를 JPQL과 enum 파라미터로 바꾸지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-17, Claude Code 워크플로 리뷰어 |
| 제안 요지 | DropRepository 목록 쿼리 두 개에 복제된 `status IN ('SCHEDULED', 'OPEN', 'SOLD_OUT')`를 JPQL로 바꾸고 `statuses: Collection<DropStatus>` 파라미터를 DropService의 상수 하나(예 LISTED_STATUSES)에서 넘긴다. |
| 왜 틀렸나 | 설계 불일치. 제안이 틀렸다기보다 설계 문서가 정한 쿼리 형태를 그대로 지키려고 쓰지 않았다. 01-ERD 인덱스 설명은 목록 조회를 status IN ('SCHEDULED','OPEN','SOLD_OUT') ORDER BY open_at으로 정한다. 이 조건 리터럴과, 구현에서 고른 커서 쿼리의 (open_at, id) 행 값 비교를 SQL에 그대로 남기려고 네이티브 SQL로 두었다. 행 값 비교는 설계 문서가 아니라 #77 구현의 선택이다. 지적의 핵심인 첫 페이지와 다음 페이지 조건의 복제는 같은 지적이 함께 낸 다른 해법(목록 조건 상수 하나)으로 없어진다. 그 대가로 목록 상태 집합은 enum이 아니라 SQL 상수 하나에 남는다. |
| 어떻게 알았나 | 설계 문서 대조. 구현 워크플로 수정 단계에서 내부 리뷰어 지적 clean-4(DropRepository.kt 15행, 심각도 낮음)가 낸 두 해법 가운데 JPQL 안을 01-ERD 124행 인덱스 설명과 대조했다. 내부 리뷰어 지적이라 GitHub 댓글이 없어 근거는 [PR #77 본문](https://github.com/moonino/dropgate/pull/77) AI 절의 기각 문단이다. 실행이나 측정으로 확인한 것은 아니다. |
| 대신 한 것 | 목록 조건을 LISTED_DROP_CONDITION 상수 하나로 두어 네이티브 쿼리 두 개가 같이 쓰고, `다음 페이지도 CLOSED 드롭을 뺀다` 테스트로 동작을 고정했다. PR #77 |
