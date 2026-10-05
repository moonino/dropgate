# 0001 Kotlin과 Spring Boot

- 상태: accepted
- 날짜: 2026-10-04
- 관련: docs/design/00-설계-개요.md 4절과 5절, docs/plan.md 1절, 이슈 #11

## 맥락

서비스 셋과 배치를 7주 안에 만들고 측정까지 끝내야 한다. 언어와 프레임워크는 모든 모듈에 걸치므로 코드를 쓰기 전에 정해야 한다. 플랜 1절의 대상 기술 첫 줄이 Kotlin이고, 나머지 여섯 기술(Kubernetes, Redis, MSA, NoSQL, 배치, 인증 인가)은 언어와 무관하게 Spring 생태계에 이미 구현체가 있다.

## 결정

Kotlin 2.x와 JDK 21 위에 Spring Boot 4를 쓰고, 코루틴과 가상 스레드는 쓰지 않는다.

- Kotlin은 이 프로젝트가 측정 수치와 함께 코드로 남기려는 일곱 기술 중 하나다. 다른 여섯은 언어를 바꿔도 되지만 이것은 바꾸면 목적이 사라진다.
- Spring Boot를 고른 이유는 필요한 것이 전부 한 생태계에 있어서다. 카카오 OAuth2 Client, JWT Resource Server, Spring Batch의 재시작 가능한 청크, Spring Data JPA와 JdbcTemplate, Testcontainers 통합. 이 중 하나라도 직접 만들면 7주가 모자란다.
- 코루틴을 쓰지 않는 이유는 실험 변수를 줄이기 위해서다. JDBC와 Spring MVC는 블로킹이고, 측정 질문(재고 정확도, 대기열 순번, 절체 초)은 동시성 모델이 아니라 Redis와 DB 경계에서 답이 난다. 코루틴을 넣으면 p99 차이가 어디서 왔는지 분리하기 어려워진다. 같은 이유로 JDK 21의 가상 스레드(`spring.threads.virtual.enabled`)도 기본값인 끔으로 둔다.

## 대안

| 대안 | 장점 | 단점 | 안 고른 이유 |
|---|---|---|---|
| Java 21 | 레퍼런스가 가장 많다. 가상 스레드, record | 대상 기술에 없다. 타입 수준 null 안전이 없고, record에는 기본값 인자와 copy가 없어 DTO와 이벤트 스키마 변경이 길어진다 | 프로젝트 목적 자체가 Kotlin이다 |
| Kotlin + 코루틴 + WebFlux + R2DBC | 높은 동시성에서 스레드 수가 적다 | Spring Batch와 JPA가 블로킹이라 스택이 둘로 갈린다. 디버깅과 트랜잭션 경계가 어려워진다 | 측정 질문이 비동기 I/O 모델로 답이 바뀌지 않는다 |
| Go | 작은 이미지, 빠른 기동 | Spring Batch 같은 재시작 가능한 배치 프레임워크와 OAuth2 Client를 직접 짜야 한다 | 7주 안에 일곱 기술을 다 못 다룬다 |

## 결과

- 모든 모듈이 같은 Gradle Kotlin DSL 멀티모듈 안에 있고, ktlint를 CI에서 강제한다(CONTRIBUTING 5절).
- JPA 엔티티는 Kotlin에서 final이 기본이라 `kotlin-spring`(allopen)과 `kotlin-jpa`(noarg) 플러그인을 공통 빌드 설정에 두고, `allOpen`에 `@Entity`, `@MappedSuperclass`, `@Embeddable`을 따로 등록한다. `kotlin-spring`만으로는 Spring 애너테이션만 열리고 엔티티는 final로 남아 지연 로딩 프록시가 동작하지 않는다.
- Spring Boot 4가 새 메이저라 라이브러리 호환 문제가 나올 수 있다. 4.1에서 막히면 4.0으로 내린다(OSS 지원 2026-12-31까지, 프로젝트 기간을 덮는다). 3.5는 OSS 지원이 2026-06-30에 끝나 후보에서 뺀다(endoflife.date/spring-boot 기준). 4.x 안에서 해결이 안 되면 이 ADR을 superseded로 바꾼다.
- 다시 볼 조건: 측정에서 스레드 고갈이 p99의 주 원인으로 드러나면 먼저 가상 스레드를 켜서 전후를 재고(스택이 그대로라 변수가 하나다), 그래도 부족하면 코루틴 도입을 새 ADR로 검토한다. 지금은 그 증거가 없다.
