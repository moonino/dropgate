# 단일 경쟁 시나리오의 사용자별 거절 SQL은 본문에 둔다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | [PR #154 제안](https://github.com/moonino/dropgate/pull/154#issuecomment-6262024935)은 사용자별 INSERT 거절 트리거의 생성과 제거를 rejectOrdersOf(userId)라는 AutoCloseable 도우미로 빼고 use로 감싸자는 내용이다. |
| 왜 틀렸나 | 과잉. 가능한 가독성 개선이지만 이 조건부 SQL은 해당 지연 구매 한 건만 거절하는 단일 경쟁 시나리오에만 필요하다. 모든 쓰기를 거절하는 기존 공통 도우미와 조건이 다르며 당시 try/finally도 정리를 보장한다. 이 제안만을 위해 테스트 추상화 범위를 더 넓힐 필요는 없다. |
| 어떻게 알았나 | 테스트. 소스에서 userId를 조건으로 거절하는 한 사용처와 finally의 트리거 및 함수 제거를 확인했다. 같은 소스를 포함한 당시 통합 스택에서 JDK 21로 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 --no-parallel ktlintCheck :common:test :order-service:test`로 common 66개와 order-service 294개 및 ktlint가 통과했고 실패, 오류와 건너뜀은 모두 0이었다. main fbe3482f 위 변경 3개 파일은 이전 Green과 바이트 동일하다. 이 제안 때문에 새 실행은 하지 않았다. |
| 대신 한 것 | 사용자별 거절 조건과 경쟁 순서를 같은 테스트에서 읽을 수 있는 head 85b4894를 유지했고, 공통 규칙 리팩터에도 이 테스트만을 위한 추상화를 추가하지 않았다. [PR #154](https://github.com/moonino/dropgate/pull/154). |
