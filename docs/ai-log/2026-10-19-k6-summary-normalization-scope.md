# k6 출력과 완료 판정을 위한 중간 객체를 추가하지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | [PR #159 제안](https://github.com/moonino/dropgate/pull/159#issuecomment-6262753512)은 print_summary와 정확성 판정이 지표 경로 및 기본값을 반복하므로 공통 요약 객체를 만든 뒤 그 객체에서 판정하자는 내용이다. |
| 왜 틀렸나 | 과잉. 없는 카운터를 출력에서 0으로 표시하는 일과 실행 성공을 판정하는 일은 책임이 다르다. 공통 중간 객체나 파일을 도입하면 표시용 기본값과 완료 조건을 같은 표현에 묶는다. 다만 필수 성공 건수의 참조 방식은 일관돼야 하므로 verify_k6_summary의 합에서 purchase_confirmed.count에 붙인 // 0 제거는 채택했다. 당시 준비본에는 이 부분만 반영했다. 후속 PR #160의 중복 검증 지적으로 합계식 자체를 제거했고 새 중간 자료 구조는 만들지 않는다. |
| 어떻게 알았나 | 문서 대조. 검토 당시 B 289줄에는 정확 요청 및 성공과 발행 완료 판정 8줄이 빠져 있고, C 초안 327줄은 verify_reconciliation과 verify_k6_summary를 처음 정의해 완료 marker 발행 전에 호출한다. C는 필수 purchase_confirmed.count를 직접 참조하며 출력은 없는 카운터를 0으로 표시한다. `bash -n work/w2/worktrees/116/load/purchase-load.sh`와 `bash -n work/w2/drafts/load/purchase-load.sh`가 모두 종료 0이었고 find_java_21 및 build_order_service 본문은 두 파일에서 같았다. 기존 manual 대조의 실제 3건 구매와 정상 및 변조 종료 0/1은 인프라 검증일 뿐 이 완료 판정 기능의 요청 10000건 검증이 아니다. 해당 리뷰 검토 당시 B/C의 실제 10000건 실행은 0회다. |
| 대신 한 것 | PR #159. 이 PR의 표시 흐름은 유지하고 후속 기능 C에서 완료 판정 함수를 도입한다. 검토 당시 필수 성공 카운터의 // 0만 제거한 준비본을 사용했다. 후속 PR #160의 [재리뷰](https://github.com/moonino/dropgate/pull/160#issuecomment-6263201426)를 반영해 합계식 자체를 제거하므로 이 참조 통일도 최종 판정에는 남지 않는다. 공통 정규화 객체나 파일은 추가하지 않는다. |
