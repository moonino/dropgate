# 재고 대조 결과의 출력과 원본 보존을 한 경계로 둔다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | print_accuracy_report 안의 tee를 main으로 옮겨 print_environment와 결과 저장 위치를 통일한다. |
| 왜 틀렸나 | 과잉. 저장 위치 통일도 가능한 정리지만 현재 함수는 대조 결과를 화면에 출력하면서 같은 원본 JSON을 보존하는 경계다. 진행 로그는 stdout에 먼저 남기고 reconcile.sh의 JSON만 파일에 저장한다. tee만 호출부로 옮기면 진행 로그도 원본 JSON에 섞이므로 로그 경계까지 바꿔야 한다. 환경 기록은 로그 자체를 저장하는 텍스트여서 두 저장 위치가 같은 책임이라고 단정하기 어렵다. 현재 소비자는 화면 출력과 JSON 파일을 각각 사용하므로 추가 실행 흐름 변경은 미룬다. |
| 어떻게 알았나 | 문서 대조. [최신 리뷰 제안](https://github.com/moonino/dropgate/pull/159#issuecomment-6262984105)을 head c95997c의 print_accuracy_report, log, main 및 집계기의 JSON 파일 읽기와 대조했다. 함수 안에서는 reconcile.sh의 stdout만 tee로 보존하고 진행 로그는 그 파이프 밖에 있다. 기존 수동 구매 3건은 `DROPGATE_MANUAL_ORDER_JAR=<보존한 최종 main JAR> bash work/w2/drafts/manual-reconcile-check.sh`로 reconcile.sh를 별도 검증한 기록이며 부하 실행 도구 검증이 아니다. 해당 리뷰 검토 당시 실제 1만 요청 부하 실행은 0회다. 이번 검토에서는 소스를 읽었고 새 테스트나 부하는 실행하지 않았다. |
| 대신 한 것 | 대조 JSON의 출력과 원본 보존 경계를 유지하고 head c95997c의 실행 흐름을 바꾸지 않는다. PR #159. |
