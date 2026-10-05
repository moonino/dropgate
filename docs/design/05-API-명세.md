# 05 API 명세

정본은 같은 폴더의 `openapi.yaml`(OpenAPI 3.1)이다. 이 문서는 요약과 오류 코드표다. 구현 뒤 springdoc이 만든 생성물과 이 파일을 비교하는 테스트를 둔다(미터엔진 OpenApiDocumentTest 방식).

## 엔드포인트 요약

| 서비스 | 메서드와 경로 | 인증 | 뜻 |
|---|---|---|---|
| auth | GET /auth/login/kakao | 없음 | 카카오로 302 |
| auth | GET /auth/callback/kakao | 없음 | 토큰 쌍 발급 |
| auth | POST /auth/token/refresh | 없음(본문 리프레시) | 회전 재발급 |
| auth | POST /auth/logout | Bearer | 무효화 |
| auth | GET /auth/me | Bearer | 내 정보 |
| auth | GET /.well-known/jwks.json | 없음 | 공개키 |
| order | POST /api/admin/drops | Bearer ADMIN | 드롭 등록 |
| order | GET /api/drops | 없음 | 목록 |
| order | GET /api/drops/{dropId} | 없음 | 단건, 남은 수량 |
| order | POST /api/drops/{dropId}/queue | Bearer | 대기열 진입 |
| order | GET /api/drops/{dropId}/queue/me | Bearer | 순번과 입장 상태 |
| order | DELETE /api/drops/{dropId}/queue/me | Bearer | 포기 |
| order | POST /api/drops/{dropId}/orders | Bearer | 구매 |
| order | GET /api/orders/me | Bearer | 내 주문 |
| order | POST /api/orders/{orderId}/cancel | Bearer | 취소 |
| notification | GET /api/notifications/me | Bearer | 내 피드 |
| batch | POST /batch/jobs/daily-settlement | 클러스터 내부 | 결산 |
| batch | POST /batch/jobs/reconciliation | 클러스터 내부 | 대조 |
| batch | GET /batch/jobs/{executionId} | 클러스터 내부 | 상태 |

## 공통 규칙

- 요청과 응답 JSON 키는 lowerCamelCase. 시각은 ISO 8601 UTC 밀리초. 식별자는 UUID 문자열
- 페이징은 전부 커서. `nextCursor`가 null이면 끝. OFFSET은 쓰지 않는다
- 오류 응답은 `{code, message, errors[]}` 하나. message는 사용자에게 그대로 보여 줄 한국어 문장이고 클라이언트는 code로 분기한다(미터엔진 MS2-332 결정과 같다)
- 503에는 Retry-After 초를 붙인다
- Bearer 검증은 common 모듈 필터 하나. JWKS 10분 캐시, 무효화 목록은 요청마다 Redis 확인. 그래서 Bearer 엔드포인트는 전부 503 AUTH_UNAVAILABLE을 낼 수 있다
- openapi.yaml은 `npx @redocly/cli lint docs/design/openapi.yaml`이 통과해야 한다. 규칙은 루트 `redocly.yaml`

## 오류 코드표

| HTTP | code | 어디서 | 뜻 |
|---|---|---|---|
| 400 | VALIDATION_FAILED | 전부 | errors에 필드별 메시지 |
| 401 | UNAUTHENTICATED | 전부 | 토큰 없음, 만료, 서명 불일치, 무효화됨, 리프레시 없음 |
| 403 | FORBIDDEN | 관리자 API, 타인 주문 취소 | |
| 403 | NOT_ADMITTED | 구매 | 입장 전이거나 입장 60초 만료 |
| 404 | DROP_NOT_FOUND | 드롭 | |
| 404 | ORDER_NOT_FOUND | 주문 | |
| 404 | JOB_EXECUTION_NOT_FOUND | 배치 | |
| 409 | DROP_NOT_OPEN | 대기열, 구매 | SCHEDULED 또는 CLOSED |
| 409 | SOLD_OUT | 대기열, 구매 | |
| 409 | ALREADY_PURCHASED | 대기열, 구매 | 취소한 사용자도 포함 |
| 409 | ORDER_ALREADY_CANCELED | 취소 | |
| 409 | DROP_CLOSED | 취소 | 마감 뒤 취소 불가 |
| 409 | JOB_ALREADY_RUNNING | 배치 | |
| 502 | KAKAO_UNAVAILABLE | 로그인 | 카카오 응답 실패나 3초 초과 |
| 503 | AUTH_UNAVAILABLE | 로그인 콜백, 재발급, 로그아웃, 모든 Bearer 엔드포인트의 토큰 검증 | 무효화 목록이나 리프레시 Redis 명령 실패나 200ms 초과. Retry-After 2 |
| 503 | STOCK_UNAVAILABLE | 대기열, 구매 | 재고와 대기열 Redis 명령 실패나 200ms 초과. Retry-After 2 |
| 503 | ORDER_PERSIST_FAILED | 구매 | Lua 성공 뒤 DB 실패, Redis 보상 완료. Retry-After 2 |
| 503 | NOTIFICATION_UNAVAILABLE | 피드 | DynamoDB 실패 |

구매에서 코드가 나오는 순서는 Lua 반환 순서와 같다. DROP_NOT_OPEN, NOT_ADMITTED, ALREADY_PURCHASED, SOLD_OUT.

## 계약 테스트

- 서비스마다 통합 테스트가 위 표의 모든 code를 적어도 한 번 낸다(미터엔진 GlobalExceptionHandlerCoverageTest 방식)
- springdoc 생성물의 경로와 응답 코드가 openapi.yaml과 같은지 비교하는 테스트 하나. 다르면 openapi.yaml을 먼저 고치고 ADR에 이유
