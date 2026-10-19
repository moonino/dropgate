# 취소 준비 값은 명시적 필드 이름으로 읽는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | [PR #157 제안](https://github.com/moonino/dropgate/pull/157#issuecomment-6262329296)은 두 취소 서비스가 cancelable.order와 cancelable.dropName 등을 반복하므로 구조 분해로 order, dropName, canceledAt 지역 변수를 만들자는 내용이다. |
| 왜 틀렸나 | 과잉. 구조 분해도 가능한 표기지만 현재 명시적 필드는 잠금과 검사로 얻은 취소 준비 값이라는 출처와 각 값의 의미를 호출부에서 드러낸다. 짧은 본문에서 이 이름을 반복하는 것이 역할을 숨기거나 같은 조건을 중복 검사하는 것은 아니다. 필드 순서보다 이름으로 읽는 현재 선택을 바꿀 기능상 근거는 없다. |
| 어떻게 알았나 | 테스트. 두 서비스가 같은 준비 값을 사용하면서 DB 경로의 재고 복구 순서와 Redis 경로의 REQUIRES_NEW를 각 소유 서비스에 유지함을 대조했다. 동일 생산 소스의 초기 순수 리팩터는 JDK 21의 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 --no-parallel ktlintCheck :common:test :order-service:test`로 common 66개와 order-service 294개 및 ktlint가 통과했다. 실패, 오류와 건너뜀은 모두 0이었다. 이번 표기 제안 때문에 생산 코드를 바꾸거나 테스트를 다시 실행하지 않았다. |
| 대신 한 것 | 취소 준비 값의 명시적 필드 이름을 유지하고 소스 변경 범위를 늘리지 않는다. [PR #157](https://github.com/moonino/dropgate/pull/157). |
