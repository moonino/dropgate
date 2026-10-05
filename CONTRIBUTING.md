# CONTRIBUTING

dropgate 저장소 규칙이다. 혼자 하는 프로젝트지만 팀 레포처럼 기록이 남게 한다. 규칙을 어겨야 하면 코드가 아니라 PR 본문에 이유를 남긴다.

## 1. 브랜치

- `main`은 항상 배포 가능한 상태다. 직접 push하지 않고 PR로만 바꾼다.
- 작업 브랜치 이름은 `<type>/<issue>-<slug>`다. 소문자, 영어, 하이픈. 예: `feat/12-redis-stock-lua`, `infra/31-kind-helm-chart`, `docs/3-adr-0005`.
- type은 커밋 type과 같다(아래 2절).
- 머지된 브랜치는 지운다. 머지는 squash다.

## 2. 커밋 메시지

- 제목은 `<type>: <한국어 설명> (#<issue>)`. 예: `feat: 재고 차감을 Redis Lua로 원자 처리한다 (#12)`.
- type: `feat` 기능, `fix` 버그, `refactor` 동작 변화 없는 구조 변경, `test` 테스트만, `docs` 문서와 ADR, `infra` 배포와 IaC와 CI, `perf` 측정과 성능, `chore` 그 외.
- 제목은 50자 안, 끝에 마침표 없음, 설명은 "무엇을 한다" 현재형.
- 본문에는 왜를 쓴다. 무엇은 diff가 말한다. 측정 결과가 있으면 수치와 재는 명령을 적는다.
- 커밋 하나에 관심사 하나. 기능과 리팩터를 섞지 않는다.
- 트레일러(Co-Authored-By 등)는 붙이지 않는다.

## 3. 이슈

- 작업 단위는 GitHub Issue다. 제목은 커밋 제목과 같은 형식에서 type을 뺀 것. 본문은 목표 한 줄과 산출물 한 줄, 해당하는 설계 문서 절.
- 라벨: type 라벨(feat, fix, refactor, test, docs, infra, perf, chore)과 주차 라벨(w1에서 w8).
- 마일스톤은 주차(w1 10-06에서 10-12 ...).

## 4. PR

- 300줄 이하(추가 + 삭제). 넘으면 동작 단위로 나눈다. 생성물(openapi 생성물, 잠금 파일)은 셈에서 뺀다.
- 제목은 커밋 제목 형식. 본문은 `.github/pull_request_template.md`를 채운다.
- CI 녹색이어야 머지한다. 사람 리뷰어가 없으므로 셀프 머지지만 본문의 체크리스트를 전부 확인한 뒤 머지한다.
- PR마다 Claude Code(Opus 5.5)가 자동 리뷰를 단다(`.github/workflows/claude-code-review.yml`). 지적은 실측이나 설계 문서로 검증한 뒤 반영하거나 기각하고, 기각 이유를 답글로 남긴다. 오탐을 그대로 반영하지 않는다. 이슈나 PR 댓글에 `@claude`를 적으면 작업을 시킬 수 있다(`claude.yml`).
- PR 본문의 AI 칸에 AI가 만든 범위와 검증 방법을 적는다. 자동 리뷰 지적마다 반영 또는 기각으로 시작하는 답글을 하나 남긴다. AI가 준 코드나 지적을 쓰지 않은 사례는 `docs/ai-log/`에 한 건씩 남기고, 주간 집계는 `docs/ai-log/weekly.md`에 적는다.
- 리뷰 워크플로는 저장소 시크릿 `CLAUDE_CODE_OAUTH_TOKEN`(Claude Max 계정, `claude setup-token`으로 발급)과 Claude GitHub 앱 설치가 필요하다.
- 설계(`docs/design/`)와 다르게 구현한 PR은 설계 문서 변경과 ADR을 같은 PR에 넣는다.
- 컨트롤러나 DTO를 바꾼 PR은 `docs/design/openapi.yaml`도 같이 고친다. 계약 테스트가 둘을 비교한다. openapi.yaml을 고쳤으면 `npx @redocly/cli lint docs/design/openapi.yaml`이 통과해야 한다.

## 5. 코드 규칙

- Kotlin 공식 코드 스타일, ktlint로 강제. CI에서 `ktlintCheck`가 돈다.
- 주석을 쓰지 않는다. KDoc도 주석이다. 설명이 필요하면 이름이나 분리를 고치고, 제약은 테스트로 고정한다.
- 이름 규칙

| 대상 | 규칙 | 예 |
|---|---|---|
| 클래스, 파일 | PascalCase, 역할 접미사(Controller, Service, Repository, Entity, Request, Response) | `DropQueueService` |
| 함수, 변수 | lowerCamelCase, 함수는 동사로 시작 | `admitNextBatch` |
| 상수 | UPPER_SNAKE_CASE | `ADMIT_TTL_SECONDS` |
| 패키지 | 소문자, 서비스.계층 | `dropgate.order.service` |
| DB 테이블, 컬럼 | snake_case, 테이블은 복수 아닌 단수 명사 | `order_ledger`, `occurred_at` |
| Redis 키 | 소문자, 콜론 구분, 소유 서비스 또는 자원이 앞 | `drop:{dropId}:stock` |
| JSON 키 | lowerCamelCase | `remainingQuantity` |
| URL | 소문자 kebab-case, 자원은 복수 | `/api/drops/{dropId}/queue/me` |
| 테스트 함수 | 한국어 문장, 백틱 | `` fun `재고가 0이면 SOLD_OUT을 돌려준다`() `` |
| 환경 변수 | UPPER_SNAKE_CASE, 접두 `DROPGATE_` | `DROPGATE_REDIS_SENTINEL_MASTER` |

- 테스트는 기능과 같은 PR에 둔다. 통합 테스트는 Testcontainers, 단위 테스트는 Mockito 없이 가능한 한 실제 객체.
- Flyway `V__` 파일은 머지 뒤 수정하지 않는다. 바꿀 것은 새 버전으로.
- null을 돌려주지 않는다. 빈 결과는 빈 컬렉션, 실패는 예외.
- 외부 라이브러리(Redis 클라이언트, DynamoDB SDK, 카카오 API)는 얇은 래퍼 한곳을 통해서만 쓴다.

## 6. ADR

- `docs/adr/NNNN-slug.md`. 번호는 네 자리, 템플릿은 `docs/adr/0000-template.md`.
- 상태는 proposed, accepted, superseded 중 하나. 번복한 결정은 지우지 않고 superseded로 바꾸고 새 ADR을 가리킨다.
- 설계 문서와 ADR이 다르면 ADR이 최신이고 설계 문서를 따라 고친다.

## 7. 측정 기록

- `docs/measurements/YYYY-MM-DD-slug.md`. 환경(어디서, 파드 수, 인스턴스), 명령, 결과 표, 해석 한 줄.
- 수치는 재는 명령과 함께만 적는다. 합성 부하임을 밝힌다. 재현 못 하는 수치는 쓰지 않는다.
- README 상단 표는 measurements의 요약이고 출처 파일을 가리킨다.

## 8. 비밀과 개인 공간

- `.env`, `*.pem`, Terraform 상태는 커밋하지 않는다. 필요한 변수 목록은 `.env.example`에 값 없이 둔다.
- `work/`는 개인 공간이고 .gitignore로 제외된다. 커밋 대상이 아닌 산출물은 전부 여기에.

## 9. 문서

- 한국어. 가운뎃점, 엠 대시, 엔 대시, 이모지, 둥근 따옴표, 물결표를 쓰지 않는다. 범위는 "에서"로 쓴다.
- 미정인 것을 확정처럼 쓰지 않는다. 추정은 추정이라 적는다.
