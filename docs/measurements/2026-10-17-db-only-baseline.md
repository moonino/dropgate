# DB만 경로 기준선: 재고 100개에 1만 요청

- 날짜: 2026-10-17 (이슈 #10의 계획일은 2026-10-12, 실제로 잰 날을 적는다)
- 질문: docs/plan.md 2절 첫 행. 재고 100개에 1만 요청을 쏘면 성공이 정확히 100건인가(초과 0, 중복 0). 이 기록은 그 질문의 1주 차 기준선이다(ADR 0005)
- 부하는 합성 부하다. 실제 사용자 트래픽이 아니라 k6가 만든 요청이고, 부하 발생기와 서비스와 DB가 한 대의 Mac에서 CPU를 나눠 쓴다

## 환경

| 항목 | 값 |
|---|---|
| 장소 | 이 Mac 한 대. Apple M5, 코어 10, 메모리 32GB, macOS 26.5.2 |
| Docker | Docker Desktop 4.84.0(Engine 29.6.2), VM에 CPU 10, 메모리 약 7.7GiB. 버전은 `docker version --format '{{.Server.Platform.Name}}, Engine {{.Server.Version}}'`, CPU와 메모리는 `docker info --format '{{.NCPU}} {{.MemTotal}}'`로 쟀고 스크립트 끝에서도 버전을 찍는다 |
| 서비스 | order-service 1개. 호스트에서 bootJar를 Temurin 21.0.12.1로 실행. 커넥션 풀과 스레드 수는 따로 설정하지 않은 Spring Boot 기본값 |
| DB | postgres:16-alpine(PostgreSQL 16.15) 컨테이너, 기본 설정. orders 스키마는 order_user 소유, Flyway 마이그레이션 전부 적용(최종 스키마는 측정 때와 같다) |
| 인증 | 측정용 RSA 키를 실행마다 즉석 생성. 공개키는 nginx:1.27.5-alpine 컨테이너가 정적 JWKS로 제공. auth-service와 카카오는 쓰지 않는다 |
| 부하 | grafana/k6:1.3.0 컨테이너. shared-iterations, 가상 사용자 500, 반복 10,000. 사용자 8,000명 중 8명마다 1명(1,000명)이 같은 토큰으로 연달아 3번, 나머지 7,000명은 한 번 요청한다. 연달은 반복은 서로 다른 가상 사용자가 거의 동시에 집는다 |
| 경로 | k6 컨테이너에서 host.docker.internal로 order-service에, order-service에서 Docker Desktop 포트 포워딩으로 PostgreSQL에 붙는다 |
| 빠진 것 | Redis와 대기열과 입장 확인 없음(1주 차 범위). 기준선에는 대기열 비용이 포함되지 않는다. 예열 없음, 실행마다 새 JVM과 새 DB |

## 명령

```sh
./load/db-only-baseline.sh
```

스크립트가 하는 일은 순서대로 다음과 같다. 필요한 도구는 Docker, JDK 21, Node.js, jq, curl, openssl이다.

1. `./gradlew :order-service:bootJar`
2. `node load/token/issue-tokens.mjs`로 RSA 키를 만들고 JWKS, 구매 요청 1만 건의 토큰 목록(사용자 8,000명), ADMIN 토큰 1개를 `load/build/db-only-baseline/`에 쓴다. 개인키는 디스크에 쓰지 않는다
3. PostgreSQL 컨테이너를 띄우고 `CREATE ROLE order_user`, `CREATE SCHEMA orders AUTHORIZATION order_user`
4. JWKS 컨테이너와 order-service를 띄우고 `/actuator/health`가 200이 될 때까지 기다린다
5. ADMIN 토큰으로 `POST /api/admin/drops`(totalQuantity 100, openAt 3초 뒤)를 보내고, 드롭 상태 스케줄러가 SCHEDULED를 OPEN으로 바꿀 때까지 `GET /api/drops/{dropId}`를 확인한다
6. `load/k6/purchase.js`로 `POST /api/drops/{dropId}/orders` 1만 건. 201, 409 SOLD_OUT, 409 ALREADY_PURCHASED 밖의 응답이 한 건이라도 있으면 k6 임계값이 깨져 스크립트가 실패한다. 결과 요약은 `load/k6/results/`(커밋하지 않음)
7. 아래 대조 SQL을 돌리고 컨테이너와 프로세스를 정리한다

같은 명령을 일곱 번 돌렸다. 앞의 네 번은 k6 임계값 `purchase_unexpected: ['count==0']`를 더하기 전이고, 요청 순서와 내용은 같다.

## 결과

전체 응답 시간(ms). 실행은 k6 결과 파일 이름의 시각(UTC)이다.

| 실행 | 요청 | 성공 201 | SOLD_OUT 409 | ALREADY_PURCHASED 409 | 그 외 | 초과 | 중복 | p50 | p95 | p99 | 최대 | 요청/초 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 20261017T043835Z | 10,000 | 100 | 9,887 | 13 | 0 | 0 | 0 | 170 | 514 | 817 | 1,283 | 2,281 |
| 20261017T045317Z | 10,000 | 100 | 9,883 | 17 | 0 | 0 | 0 | 151 | 413 | 652 | 881 | 2,649 |
| 20261017T045338Z | 10,000 | 100 | 9,879 | 21 | 0 | 0 | 0 | 149 | 446 | 698 | 1,024 | 2,631 |
| 20261017T045821Z | 10,000 | 100 | 9,885 | 15 | 0 | 0 | 0 | 186 | 566 | 1,164 | 1,826 | 1,756 |
| 20261017T050152Z | 10,000 | 100 | 9,883 | 17 | 0 | 0 | 0 | 210 | 673 | 971 | 1,780 | 1,839 |
| 20261017T050217Z | 10,000 | 100 | 9,881 | 19 | 0 | 0 | 0 | 228 | 691 | 861 | 1,423 | 1,733 |
| 20261017T050241Z | 10,000 | 100 | 9,874 | 26 | 0 | 0 | 0 | 220 | 704 | 905 | 1,260 | 1,793 |

응답별 응답 시간(ms). k6의 `http_req_duration{status:201}`과 `http_req_duration{status:409}`다.

| 실행 | 201 p50 | 201 p95 | 201 p99 | 409 p50 | 409 p95 | 409 p99 |
|---|---|---|---|---|---|---|
| 20261017T043835Z | 456 | 655 | 666 | 169 | 460 | 817 |
| 20261017T045317Z | 381 | 521 | 543 | 151 | 372 | 653 |
| 20261017T045338Z | 381 | 546 | 567 | 148 | 414 | 698 |
| 20261017T045821Z | 831 | 1,160 | 1,178 | 186 | 486 | 1,142 |
| 20261017T050152Z | 547 | 702 | 718 | 209 | 655 | 972 |
| 20261017T050217Z | 520 | 731 | 747 | 228 | 684 | 862 |
| 20261017T050241Z | 566 | 766 | 784 | 219 | 694 | 905 |

초과와 중복은 k6가 아니라 측정 뒤 DB에서 센 값이다. 일곱 실행 모두 같은 결과였다.

| total_quantity | remaining_quantity | status | all_orders | confirmed_orders | oversold | duplicated_users | stock_mismatch | confirmed_ledger_entries |
|---|---|---|---|---|---|---|---|---|
| 100 | 0 | SOLD_OUT | 100 | 100 | 0 | 0 | 0 | 100 |

대조 SQL(`load/sql/verify-purchase-accuracy.sql`, `psql -v drop_id=<드롭 id>`로 실행):

```sql
WITH drop_orders AS (
    SELECT user_id, status FROM orders.orders WHERE drop_id = :'drop_id'
),
confirmed AS (
    SELECT count(*) AS confirmed_orders FROM drop_orders WHERE status = 'CONFIRMED'
),
duplicated AS (
    SELECT count(*) AS duplicated_users FROM (
        SELECT user_id FROM drop_orders GROUP BY user_id HAVING count(*) > 1
    ) AS repeated_buyers
),
ledger AS (
    SELECT count(*) AS confirmed_ledger_entries FROM orders.order_ledger WHERE drop_id = :'drop_id' AND entry_type = 'CONFIRMED'
)
SELECT
    d.total_quantity,
    d.remaining_quantity,
    d.status,
    (SELECT count(*) FROM drop_orders) AS all_orders,
    c.confirmed_orders,
    greatest(c.confirmed_orders - d.total_quantity, 0) AS oversold,
    dup.duplicated_users,
    d.total_quantity - d.remaining_quantity - c.confirmed_orders AS stock_mismatch,
    l.confirmed_ledger_entries
FROM orders.drops d, confirmed c, duplicated dup, ledger l
WHERE d.id = :'drop_id';
```

## 해석

같은 사용자의 재요청 2,000건이 섞인 1만 요청에서 조건부 UPDATE 한 번과 UNIQUE만으로 일곱 실행 모두 성공이 정확히 100건(초과 0, 중복 0, 재고 차 0)이었고, 서비스 1개의 전체 p99 652에서 1,164ms가 2주 차 Redis 경로와 같은 명령으로 비교할 기준선이다.

## 비교할 때 지킬 것

- 재요청 2,000건 중 ALREADY_PURCHASED는 실행마다 13에서 26건뿐이고 나머지는 SOLD_OUT이다. ADR 0005대로 DB 경로는 차감이 UNIQUE 확인보다 먼저라, 품절 뒤 도착한 재요청은 SOLD_OUT을 받는다. 2주 차 Redis 경로는 같은 재요청에 ALREADY_PURCHASED를 돌려주므로 두 409의 분포는 경로끼리 다르게 나오는 것이 맞다. 중복 0의 근거는 응답 분포가 아니라 DB의 duplicated_users다.
- ALREADY_PURCHASED가 0이 아니라는 것은 같은 사용자의 요청이 재고가 남아 있을 때 UNIQUE까지 닿았다는 뜻이다. 따라서 이 부하는 UNIQUE 방어를 실제로 거치며, 방어가 깨지면 duplicated_users로 드러난다.
- 201의 p50이 409보다 높다. 원인은 따로 재지 않았다.
- 실행마다 p99가 크게 움직였다(652에서 1,164ms). 마지막 묶음 직후 `uptime`의 1분 load average가 6.27이었고 `ps -Ao pcpu,comm -r`에서 화상 회의 앱이 CPU 65.6%를 쓰고 있었다. 부하 발생기와 서비스와 DB가 같은 Mac을 나눠 쓰므로, 전후 비교는 한 번이 아니라 같은 명령 여러 번의 범위끼리 하고 다른 무거운 앱을 끈 상태에서 한다.
- 예열이 없어 JIT 전 요청이 포함된다. 2주 차 측정도 같은 스크립트로 새 JVM에서 시작해야 공정하다.
