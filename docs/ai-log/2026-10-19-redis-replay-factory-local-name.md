# 연결 팩토리의 기존 이름을 유지한다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-19, Claude Code 자동 리뷰 제안(요약 댓글의 제안) |
| 제안 요지 | RedisCommandReplayTest의 connections를 connectionFactory로 바꾼다 |
| 왜 틀렸나 | 과잉. 이 이름은 저장소 RedisConnectionTest에서도 연결 팩토리를 가리키고 정적 타입과 createConnectionFactory 호출이 역할을 드러낸다. 다른 클래스의 소켓 집합과 범위가 겹치지 않아 추가 이름 변경을 하지 않았다 |
| 어떻게 알았나 | 코드 대조와 테스트. [재리뷰](https://github.com/moonino/dropgate/pull/125#issuecomment-6260405060)에서 필수 문제는 없었고 실제 응답 유실 테스트와 CI가 통과했다 |
| 대신 한 것 | 기존 지역 이름과 명시적 팩토리 타입을 유지한다. PR #125 |
