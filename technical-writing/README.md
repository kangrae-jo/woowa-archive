# DB Polling 재구성과 신뢰성 검증 기록

과거 커켓몬의 이미지 생성 구조를 현재 코드로 재구성하고, 실패를 먼저 증명한 뒤 신뢰성을 단계적으로 보강한다.

## 결론

**채택:** `technical-writing`을 Java 21, Spring Boot, Spring Data JPA, 로컬 MySQL 기반의 독립 Gradle 프로젝트로 전환했다. `baseline`은 02단계의 의도된 결함을 유지하고, `hardened`는 04단계 신뢰성 설계를 제공한다. 기본 프로필은 `hardened`다.

**보류:** 로컬 MySQL 인증 환경변수가 제공되지 않아 MySQL 통합 테스트와 두 프로필의 실제 기동은 아직 완료하지 못했다. H2로 대체하거나 테스트를 생략하지 않았다.

## 기술 기준

| 항목 | 선택 | 근거와 상태 |
| --- | --- | --- |
| Java | 21 | 유지 |
| Spring Boot | 3.5.16 | 지원 중인 3.x 안정 패치. Java 21과 Gradle 8.14 호환 |
| Spring Data JPA | 3.5.13 | Boot 의존성 관리 결과 |
| Hibernate ORM | 6.6.53.Final | Boot 의존성 관리 결과 |
| MySQL Connector/J | 9.7.0 | Boot 의존성 관리 결과 |
| MySQL Server | 확인 필요 | 로컬 클라이언트 `9.2.0`은 서버 버전 근거로 사용하지 않음 |

버전 선택 근거는 [Spring Boot 3.5 시스템 요구사항](https://docs.spring.io/spring-boot/3.5/system-requirements.html)과 [Boot 관리 의존성 목록](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html)이다. Boot 4로의 추가 메이저 전환은 현재 실습 범위에 필요하지 않아 적용하지 않았다.

## 용어

- **2025년 당시 안정화**: 이미지 생성 대상을 `Prompt` 테이블로 분리하고, 연속 요청과 생성 개수를 제한한 대응.
- **현재 신뢰성 개선**: 작업 상태, 원자적 선점, 처리 타임아웃 복구, 재시도, 멱등한 결과 반영을 추가하는 작업.

두 표현은 서로 다른 시점과 범위를 가리킨다.

## 외부 워커 작업 전달 방식 선택

외부 워커는 작업 실행 주체이고, 아래 항목은 **웹 서버가 워커에게 작업을 전달하는 방식**이다.

| 작업 전달 방식 | 구조 | 장점 | 제약 | 판단 |
| --- | --- | --- | --- | --- |
| 인메모리 큐·`@Async` | 웹 서버 메모리 → 내부 스레드 | 구현이 가장 단순함 | 서버 종료 시 작업 유실 가능, 웹과 작업 자원 공유 | **거부** |
| 직접 HTTP 호출 | 웹 서버 → 워커 API | 즉시 전달, 구조가 직관적 | 워커 장애가 웹 요청에 전파됨, 재시도·작업 보존을 별도로 구현 | **거부** |
| **RDB Job Queue** | 웹 서버 → Job 테이블 ← 외부 워커 Polling | 도메인 데이터와 Job을 한 트랜잭션으로 저장, 상태 조회 용이, 추가 인프라 없음 | Polling 부하, 선점·재시도·타임아웃을 직접 구현 | **채택** |
| RabbitMQ·SQS | 웹 서버 → 작업 큐 → 외부 워커 | 작업 분배, 재전달, DLQ 등 큐 기능 제공 | 브로커 운영 필요, DB와 메시지 저장의 원자성 문제 | **현재 비범위** |
| Kafka | 웹 서버 → Kafka → 외부 워커 | 높은 처리량, 다중 소비자, 이벤트 보존·재처리 | Outbox 필요 가능성, Offset·중복 처리 관리, 운영 복잡도 증가 | **현재 비범위** |

### 선택한 방식

| 항목 | 판단 |
| --- | --- |
| 실행 구조 | 웹 서버와 이미지 생성 워커를 별도 JVM으로 실행 |
| 전달 방식 | MySQL 기반 RDB Job Queue |
| 작업 등록 | Monster와 Job을 같은 DB 트랜잭션으로 저장 |
| 작업 발견 | 외부 워커가 Job 테이블을 Polling |
| 동시 처리 | 조건부 UPDATE로 작업을 원자적으로 선점 |
| 장애 복구 | 처리 타임아웃과 재시도로 복구 |
| 중복 대응 | 선점 토큰과 멱등한 결과 반영 |
| 선택 이유 | 현재 규모에서는 Kafka 운영 비용보다 단일 DB 트랜잭션과 단순한 상태 관리의 이점이 큼 |
| 전환 조건 | Polling 부하, 대규모 지속 트래픽, 다중 소비자, 이벤트 재처리 요구가 실제로 발생할 때 |

한 줄로 정리하면 다음과 같다.

> **현재 요구사항은 대규모 이벤트 스트리밍보다 작업 등록의 원자성, 상태 조회와 장애 복구가 중요하므로 외부 워커와 MySQL 기반 RDB Job Queue를 선택한다.**

## 단계별 문서

| 단계 | 내용 | 상태 |
| --- | --- | --- |
| [01](./01-past-structure-and-limitations/README.md) | 과거 구조와 기록의 한계 | 완료 |
| [02](./02-minimal-db-polling/README.md) | JPA 기반 최소 DB Polling 기준 구현 | 구현 완료·MySQL 검증 확인 필요 |
| [03](./03-failure-reproduction/README.md) | 기준 구현의 잠재적 신뢰성 문제 5개 재현 | 이식 완료·MySQL RED 결과 확인 필요 |
| [04](./04-reliability-hardening/README.md) | JPA 기반 DB Job Queue 신뢰성 개선 | 구현 완료·MySQL 검증 확인 필요 |
| [05](./05-regression-verification/README.md) | 동일 실패 테스트 전체 회귀 검증 | 보류 |
| [06](./06-final-300-job-measurement/README.md) | 현재 구현에 300개 작업 입력 후 측정 | 보류 |

01단계의 과거 사실은 이번 프레임워크 전환으로 변경하지 않는다.

## 로컬 MySQL 준비

애플리케이션 DB와 테스트 DB를 분리한다. 아래 이름은 예시다. 기존 DB를 삭제하거나 `create-drop`으로 초기화하지 않는다.

```sql
CREATE DATABASE IF NOT EXISTS technical_writing
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS technical_writing_test
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

접속 정보는 환경변수로만 전달한다. URL에는 UTC 세션 설정을 명시한다. 비밀번호는 저장소에 기록하지 않는다.

```bash
export TECHNICAL_WRITING_DB_URL='jdbc:mysql://127.0.0.1:3306/technical_writing?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
export TECHNICAL_WRITING_DB_USERNAME='application_user'
export TECHNICAL_WRITING_DB_PASSWORD='...'

export TECHNICAL_WRITING_TEST_DB_URL='jdbc:mysql://127.0.0.1:3306/technical_writing_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
export TECHNICAL_WRITING_TEST_DB_USERNAME='test_user'
export TECHNICAL_WRITING_TEST_DB_PASSWORD='...'
```

스키마는 프로필별 명시적 SQL을 `IF NOT EXISTS`로 적용하고 `ddl-auto=validate`로 매핑을 검사한다.

- baseline: [`db/baseline/schema.sql`](./02-minimal-db-polling/src/main/resources/db/baseline/schema.sql)
- hardened: [`db/hardening/schema.sql`](./04-reliability-hardening/src/main/resources/db/hardening/schema.sql)

테스트는 전용 테스트 DB의 위 네 테이블만 명시적으로 정리한다. 다른 DB나 다른 애플리케이션 테이블은 정리 대상이 아니다.

## 실행

```bash
cd technical-writing

# 기본 실행: hardened
./gradlew bootRun

# 기준 구현
./gradlew bootRun --args='--spring.profiles.active=baseline'

# 개선 구현
./gradlew bootRun --args='--spring.profiles.active=hardened'
```

`baseline`과 `hardened`를 함께 활성화하면 컨텍스트 초기화를 명시적으로 거부한다. 웹 의존성이 없으며 `web-application-type=none`으로 실행한다.

## 테스트

```bash
./gradlew test --rerun-tasks
./gradlew failureTest --rerun-tasks
```

- `test`: `failure-reproduction` 태그를 제외한다. 02 기본 동작, 04 신뢰성 불변식, 프로필·JPA·SQL 매핑 검증을 실행한다.
- `failureTest`: `baseline`을 대상으로 03단계 RED 테스트 5개만 실행한다. 다섯 테스트가 해당 불변식 assertion에서 실패해야 하며 종료 코드 `1`이 기대 결과다.
- 접속 오류, 스키마 검증 오류, 프록시·매핑 오류는 RED 재현 성공이 아니다.

## 현재 검증 결과

2026-09-08 기준.

| 명령 | 결과 |
| --- | --- |
| `./gradlew compileJava --rerun-tasks` | 성공 |
| `./gradlew compileTestJava --rerun-tasks` | 성공 |
| `./gradlew bootJar --rerun-tasks` | 성공 |
| 프로필 동시 활성화 거부 단위 테스트 | 성공 |
| `./gradlew test --rerun-tasks` | 39개 발견, DB 비의존 테스트 1개 성공, 38개 컨텍스트 초기화 실패 |
| `./gradlew failureTest --rerun-tasks` | 5개 발견, 5개 컨텍스트 초기화 실패. 의도한 RED 결과 아님 |
| baseline·hardened 실제 기동 | 확인 필요 — MySQL 인증 환경변수 없음 |

두 테스트 명령의 직접 원인은 미설정 상태의 `${TECHNICAL_WRITING_TEST_DB_URL}`이 JDBC URL로 전달된 것이다. 로컬 MySQL 기본 접속도 `root@localhost`의 비밀번호 없는 인증을 거부했다. 따라서 서버 버전, JPA 스키마 검증과 RED assertion 값은 확인하지 못했다.

## 현재 범위

HTTP API, 메시지 브로커, Heartbeat, 실제 AI Worker·GCS 연동은 비범위다. 05단계 전체 회귀 검증과 06단계 300개 작업 측정은 보류한다.
