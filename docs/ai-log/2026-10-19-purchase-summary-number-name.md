# 검증한 수치를 반환하는 내부 함수 이름을 유지한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | number를 require_non_negative로 바꿔 변환이 아닌 유한한 0 이상 수치의 검증임을 드러낸다. |
| 왜 틀렸나 | 과잉. 검증을 드러내는 이름도 가능한 개선이지만 현재 함수는 수치 타입과 유한성 및 범위를 확인하고 입력값을 그대로 반환하는 짧은 수치 경계다. 호출부는 지표를 읽으며 이 반환값을 사용하므로 number를 단순 변환으로 오해한다고 단정하기 어렵다. 이번 작업에서 내부 이름까지 바꿀 기능상 근거는 없어 현재 이름을 유지한다. |
| 어떻게 알았나 | 테스트와 문서 대조. [리뷰 제안](https://github.com/moonino/dropgate/pull/158#issuecomment-6262850748)을 head 5fccc9d의 number와 trend 및 read_run 호출부에 대조했다. 함수는 값을 변환하지 않으며 현재 작업 파일은 해당 head와 바이트가 같다. 기존 `python3 work/w2/drafts/summarizer-policy-regression.py` 기록은 모의 fixture 6사례를 통과했다. 정상 모의 실행 10개의 old/new stdout은 21927바이트로 같았고, 입력 9개와 순서 반전, 중복 runId, hash 변경, validated=false는 두 구현 모두 거절했다. 이 검증의 실제 부하 실행은 0회이며 새 테스트는 실행하지 않았다. |
| 대신 한 것 | 검증된 수치를 그대로 돌려주는 number와 head 5fccc9d를 유지한다. PR #158. |
