# 보상 실패의 리스너 설치와 관측은 한 장애 범위로 둔다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | [PR #154 제안](https://github.com/moonino/dropgate/pull/154#issuecomment-6262024935)은 failCompensation에서 예외를 꺼내는 일을 호출부로 옮기고 도우미는 deleteStockBeforeRollback처럼 키 삭제만 맡기자는 내용이다. |
| 왜 틀렸나 | 과잉. 현재 도우미는 지정한 action 동안만 롤백 리스너를 설치하고 finally에서 이전 리스너 목록을 복원한 뒤 해당 action의 예외를 전달하는 하나의 테스트 장애 범위다. 예외를 돌려주는 것은 두 사용처가 원래 오류와 보상 실패 카운터를 각각 검증하도록 돕는다. 분리도 가능하지만 두 호출부에 runCatching을 반복하게 하며 현재의 한시적 장애 범위를 더 명확히 만든다는 근거는 부족하다. |
| 어떻게 알았나 | 테스트. 두 사용처, action 실행 한 번, finally의 리스너 복원과 예외 없는 실행의 checkNotNull 실패를 대조했다. 실패 격리를 제거한 변이에서는 관련 8개 테스트가 모두 실패했고, 복원한 동일 소스를 포함한 당시 통합 스택은 JDK 21의 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 --no-parallel ktlintCheck :common:test :order-service:test`로 common 66개와 order-service 294개 및 ktlint가 통과했다. 실패, 오류와 건너뜀은 모두 0이었다. main fbe3482f 재배치의 변경 3개 파일은 이전 Green과 바이트 동일하며 새 실행은 하지 않았다. |
| 대신 한 것 | 리스너 설치와 복원 및 action 결과를 묶은 테스트 전용 도우미와 head 85b4894를 유지한다. [PR #154](https://github.com/moonino/dropgate/pull/154). |
