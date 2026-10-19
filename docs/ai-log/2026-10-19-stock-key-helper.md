# 재고 키는 공통 드롭 키 생성기를 사용한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | createDropRedisKey의 stock 인수를 stockKey 도우미로 추가 분리한다. [리뷰](https://github.com/moonino/dropgate/pull/136#issuecomment-6260937139) |
| 왜 틀렸나 | 과잉. 키 형식은 이미 공통 createDropRedisKey 한 곳이 정하며 두 읽기 경로는 같은 stock 자원을 인수로 사용한다. 두 호출을 위한 별도 도우미는 형식 정본이나 검증 경계를 바꾸지 않는다. |
| 어떻게 알았나 | 테스트. [리뷰 제안](https://github.com/moonino/dropgate/pull/136#issuecomment-6260937139)을 공통 createDropRedisKey와 실제 명령 리스너 결과에 대조했다. JDK 21의 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 ./gradlew --max-workers=2 ktlintCheck :order-service:test`로 단건 GET과 목록 MGET을 포함한 order 193개가 통과했다. |
| 대신 한 것 | 공통 드롭 키 생성기를 유지하고 읽기 함수에서 자원 이름을 직접 드러낸다. PR #136 |
