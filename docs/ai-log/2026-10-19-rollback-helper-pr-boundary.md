# 롤백 장애 도우미는 바로 다음 소비 PR을 위한 준비로 둔다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | [PR #151 제안](https://github.com/moonino/dropgate/pull/151#issuecomment-6261666979)은 아직 호출하지 않는 failNextRollback과 TransactionSystemException import를 삭제하고 실제 소비 PR에서 도입하자는 내용이다. |
| 왜 틀렸나 | 과잉. 이동 자체가 잘못된 제안은 아니다. 다만 당시 Redis Flow head a0dcf81은 `git diff 16befc68 a0dcf81 --numstat` 기준 294줄이며 이 도우미와 import 약 10줄을 옮기면 300줄 제한을 넘는다. 구체 소비자인 RedisCommitUnknownTest의 롤백 실패 래퍼 사례는 이미 실제 PostgreSQL과 Redis로 Green을 확인했다. 지금 삭제하면 별도 테스트 준비 PR을 추가로 나누어야 하므로 일시적 준비 코드를 허용하는 현재 분할을 유지한다. |
| 어떻게 알았나 | 테스트와 문서 대조. JDK 21에서 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 --no-parallel ktlintCheck :common:test :order-service:test`를 실행했고 PR #151의 common 66개와 order-service 255개가 통과했다. 당시 소비자를 포함한 로컬 스택도 같은 명령으로 common 66개와 order-service 294개가 통과했다. 실패, 오류와 건너뜀은 모두 0이다. work/w2/drafts/redis-commit-unknown-green.xml은 도우미를 실제 호출하는 2개 테스트의 성공 증거다. |
| 대신 한 것 | PR #151의 검증된 head 43fa09c를 유지하고 squash 본문에 임시 준비 범위와 구체 소비자를 기록했다. PR 본문에 새 절을 추가하지는 않았다. 구체 소비자와 취소 경계는 [PR #153](https://github.com/moonino/dropgate/pull/153)에 포함했다. |
