# 서비스 준비와 outbox 완료의 대기 예산을 구분한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | [PR #159 제안](https://github.com/moonino/dropgate/pull/159#issuecomment-6262753512)은 wait_for_outbox의 조건을 분리해 wait_until을 재사용하고 값이 같은 OUTBOX_WAIT_ATTEMPTS를 제거하자는 내용이다. |
| 왜 틀렸나 | 과잉. 서비스 준비와 outbox 발행 완료는 서로 다른 대기 예산이다. 현재 두 값이 모두 60이라는 이유만으로 한 상수에 묶으면 독립적으로 바꿀 정책을 결합한다. 현재 짧은 루프는 DB의 PENDING 조회와 발행 완료 대기라는 뜻이 바로 드러나므로 공통화를 위한 추가 구조가 필수는 아니다. |
| 어떻게 알았나 | 문서 대조. 리뷰와 B의 289줄 및 C 초안의 327줄 소스를 대조해 WAIT_ATTEMPTS=60과 OUTBOX_WAIT_ATTEMPTS=60이 다른 단계에 쓰임을 확인했다. `bash -n work/w2/worktrees/116/load/purchase-load.sh`와 `bash -n work/w2/drafts/load/purchase-load.sh`는 모두 종료 0이었다. 두 파일의 find_java_21과 build_order_service 본문도 같아 JDK 21 선택과 빌드 연결 방식이 유지됐다. 기존 수동 증거 work/w2/drafts/evidence/manual-reconcile-20261019T080411Z-6fbe576a127be45a/passed.json은 재고 10개 중 실제 구매 3건의 정상 대조 종료 0과 stock 7을 8로 바꾼 대조 종료 1을 기록한다. 이는 인프라와 대조 도구 검증이며 B/C의 요청 10000건 실행이나 outbox 대기 예산의 실측 증거가 아니다. 해당 리뷰 검토 당시 실제 1만 요청 부하 실행은 0회다. |
| 대신 한 것 | PR #159. 이 PR의 별도 대기 루프와 상수를 유지한다. 선택 제안 2번의 완료 판정 함수는 B에 섞지 않고 후속 완료 기록 기능 C에서 처음 도입하도록 분리한다. 이번 검토에서는 일지만 작성했다. |
