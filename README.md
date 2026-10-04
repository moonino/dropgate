# dropgate

한정판 드롭 플랫폼. 기술 실험용 B2C 개인 프로젝트. Kotlin, Spring Boot 4, Kubernetes, Redis(Sentinel, Streams), DynamoDB, Spring Batch, 카카오 OAuth2와 JWT. 2026-10-06 착수, 7주 + 버퍼 1주.

측정 수치 표는 구현 뒤 여기 맨 위에 온다. 질문 목록은 `docs/plan.md` 2절.

## 무엇을 하는가

사용자는 카카오로 로그인해 한정 수량 드롭의 접속 대기열에 들어가고, 입장되면 60초 안에 1인 1개를 구매한다. 주문은 PostgreSQL 불변 원장에 남고, 이벤트가 Redis Streams를 거쳐 DynamoDB 알림 피드에 쌓인다. 배치가 일별 결산과 Redis 대 DB 대조를 돈다. 결제는 없다.

## 문서

| 경로 | 내용 |
|---|---|
| docs/design/ | 설계 정본. 개요, ERD, Redis 키, DynamoDB, 이벤트, API(openapi.yaml), 배치, 쿠버네티스, 시퀀스 |
| docs/plan.md | 대상 기술, 측정 질문, 주차 계획, ADR 목록, 비용, 축소 순서 |
| docs/adr/ | 구현 중 결정 기록(생성 예정) |
| CONTRIBUTING.md | 브랜치, 커밋, 이슈, PR, 코드와 이름 규칙, ADR, 측정 기록 |
| AGENTS.md | 코딩 에이전트 규칙 |

## 방식

슬라이스가 아니라 설계 선행이다. docs/design/을 먼저 확정했고 docs/plan.md의 주차 순서로 구현한다. 설계를 바꾸려면 설계 문서를 먼저 고치고 docs/adr/에 이유를 남긴다.
