# 완료 JSON 표식을 파일 이름으로 대체하지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | [PR #160 자동 리뷰](https://github.com/moonino/dropgate/pull/160#issuecomment-6263291405)의 선택 제안 3은 파일 존재와 validated 접두사가 완료를 보장하므로 JSON의 validated 표식을 없애자는 것이다. |
| 왜 틀렸나 | 테스트 불일치. 파일이 존재하고 완료 파일처럼 이름 지어졌다는 사실은 그 JSON이 지원하는 스키마의 완료 문서라는 뜻이 아니다. 소비자는 명령행으로 받은 외부 JSON의 schemaVersion과 validated가 정확히 true인지 검사한다. 이름만 바꾼 임의 파일도 존재할 수 있으므로 파일 존재와 문서 상태는 다른 조건이다. 이 검사는 서명이나 고의 위조 방지가 아니라 입력 문서 계약 확인이다. |
| 어떻게 알았나 | 테스트와 문서 대조. load/summarize-purchase-runs.py의 read_run은 파일을 읽은 뒤 schemaVersion 1과 validated is True를 검사한다. 기존 work/w2/drafts/summarizer-policy-regression.py의 6개 모의 case에서 정상 10개 입력의 old/new 출력은 21,927바이트로 동일했고, 실제로 존재하는 완료 JSON의 validated만 false로 바꾼 case는 양쪽 모두 exit 1과 빈 stdout으로 거절했다. 근거는 work/w2/drafts/summarizer-policy-regression.log다. 검토 시점의 실제 1만 요청 부하 측정은 0회이며 새 회귀나 부하는 실행하지 않았다. |
| 대신 한 것 | producer의 완료 표식과 consumer의 schemaVersion 및 완료 상태 검사를 유지한다. 원본 재고와 k6 성공 정책을 소비자에서 다시 판정하지 않는다. PR #160 |
