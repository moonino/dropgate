# 측정 전후 프로세스 생존 검사는 현재 범위에 둔다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | [PR #159 제안](https://github.com/moonino/dropgate/pull/159#issuecomment-6262896496)은 측정 전후의 kill -0 검사를 ensure_order_service_running 같은 함수로 묶고 실패 때 로그를 남기자는 내용이다. 같은 제안은 [최신 재리뷰](https://github.com/moonino/dropgate/pull/159#issuecomment-6262984105)에서도 반복됐다. |
| 왜 틀렸나 | 과잉. 두 검사는 같은 시점의 중복 판정이 아니라 부하 실행 전과 후에 기록한 서비스 PID가 살아 있는지 확인하는 서로 다른 경계다. 한 줄인 생존 검사 자체가 분명하며 이름 있는 함수로 바꾸어야 동작이 보장되는 것은 아니다. 또 실패하면 아무 로그 없이 끝난다는 설명과 달리 EXIT cleanup은 서비스 로그 파일이 있는 실패 경로에서 안내와 로그 끝부분 최대 40줄을 출력한다. 추가 진단 함수도 가능하지만 현재 기능의 필수 보완으로 볼 근거는 없다. |
| 어떻게 알았나 | 문서 대조. `git show c95997c36abe7b460ea39e637aabef9a343c5aa4:load/purchase-load.sh`로 최종 소스를 읽어 리뷰와 대조했다. main의 부하 전후 검사 2곳, set -euo pipefail, trap cleanup EXIT와 cleanup의 실패 상태 및 파일 존재 조건, tail -n 40을 확인했다. 준비 단계의 생존 및 인증 health 검사도 별도로 유지된다. 이번에는 소스 대조만 했고 서비스나 부하 실행 및 새 테스트를 수행하지 않았다. |
| 대신 한 것 | 최종 head c95997c의 측정 전후 생존 검사와 기존 실패 정리를 유지한다. 선택 제안의 전체 추출은 추가하지 않고 기각 일지만 남긴다. [PR #159](https://github.com/moonino/dropgate/pull/159). |
