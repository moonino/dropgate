# 실행 기록의 해시 함수 추출은 현재 범위에서 보류한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | [PR #160 제안 2](https://github.com/moonino/dropgate/pull/160#issuecomment-6263291405)는 write_validated_run 안의 SHA-256 계산 파이프 세 곳을 sha256_of 함수로 추출하자는 내용이다. |
| 왜 틀렸나 | 과잉. 같은 파이프를 함수로 묶는 개선은 가능하지만 현재 세 줄은 한 함수에 연속해서 있으며 JAR, 요약과 대조 파일이라는 서로 다른 세 입력의 해시를 만든다. 같은 정확성 조건을 여러 경계에서 다시 판정하는 구조는 아니다. 계산 방식이 흩어져 있거나 다른 호출자가 있는 상태도 아니므로 완료 기록 기능에 독립 공통화 리팩터를 추가할 필수 근거는 부족하다. 함수 추출 자체가 잘못됐다는 판단이 아니라 이번 기능 범위에서 보류하는 선택이다. |
| 어떻게 알았나 | 문서 대조. `git show 4e568b0185b42d44787b5cce3e9d2a5cd0cf14ef:load/purchase-load.sh`와 `rg -n 'shasum\|sha256_of\|write_validated_run' load/purchase-load.sh`로 검토한 소스의 268에서 270행을 확인했다. shasum 사용은 write_validated_run 안의 세 곳이며 각각 shasum -a 256 뒤 첫 필드를 고르는 동일 파이프다. 계산 결과는 같은 완료 기록의 jarSha256, summarySha256과 reconciliationSha256에 각각 들어간다. 검토 당시 실제 10000건 부하는 0회였으며 이 판단은 소스 대조에 한정한다. 소스 변경, 서비스 실행이나 새 테스트는 수행하지 않았다. |
| 대신 한 것 | 현재 세 입력의 명시적인 해시 계산과 임시 파일 작성 후 mv로 완료 기록을 남기는 동작을 유지한다. sha256_of 추출은 추가하지 않고 개인 기각 일지만 남긴다. [PR #160](https://github.com/moonino/dropgate/pull/160). |
