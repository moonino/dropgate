# 검증 완료 실행 하나의 읽기와 요약 조립을 함께 둔다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | read_run의 완료 기록과 출처 검증을 별도 함수로 떼어 실행 요약 조립과 분리한다. |
| 왜 틀렸나 | 과잉. 분리도 가능한 구조 개선이지만 현재 read_run은 검증 완료 실행 하나를 읽어 요약 값으로 반환하는 짧은 조립 경계다. 파일 hash 확인과 수치 및 trend 읽기는 이미 각 함수에 맡기며 완료 기록과 대조 보고서가 맞는지 확인한 뒤 같은 실행의 요약을 만든다. 추가 중간 반환값과 함수를 도입하는 것이 이 단일 읽기 책임을 더 명확히 한다는 근거는 부족해 범위를 넓히지 않는다. |
| 어떻게 알았나 | 테스트와 문서 대조. [리뷰 제안](https://github.com/moonino/dropgate/pull/158#issuecomment-6262850748)을 head 5fccc9d의 read_run과 read_verified, number, trend 경계에 대조했다. 현재 작업 파일은 해당 head와 바이트가 같다. 기존 `python3 work/w2/drafts/summarizer-policy-regression.py` 기록은 모의 fixture 6사례를 통과했다. 정상 모의 실행 10개의 old/new stdout은 21927바이트로 같았고, 입력 9개와 순서 반전, 중복 runId, hash 변경, validated=false는 두 구현 모두 거절했다. 이 검증의 실제 부하 실행은 0회이며 새 테스트는 실행하지 않았다. |
| 대신 한 것 | 검증 완료 실행을 한 번 읽고 요약으로 반환하는 read_run과 head 5fccc9d를 유지한다. PR #158. |
