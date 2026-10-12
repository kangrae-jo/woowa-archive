# 06. 300건 기본 검증과 추가 부하 측정

## 결론 🧪

**채택:** 04 Web Server, 04 Image Worker, Measurement Application을 별도 JVM으로 실행해 HTTP 요청 300건을 기본 검증으로 측정한다. Measurement는 04의 Java 클래스를 import하지 않고 HTTP와 MySQL만 사용한다.

300건 기본 검증이 통과하면 요청 수 또는 HTTP 동시성을 늘려 추가 부하를 측정한다. 300건은 최대 처리량을 주장하기 위한 수치가 아니라, 완료·실패·미종결·누락·재시도를 확인할 기본 검증 조건이다.

측정 프로그램과 실행 환경 설정은 준비했다. 실제 300건 측정과 결과 파일 생성은 아직 실행하지 않았으며, 추가 부하 측정도 미실행이다.

```mermaid
flowchart LR
    M[Measurement Application] -->|POST /jobs·GET /jobs/{jobId}| W[04 Web Server]
    W --> DB[(technical_writing_measurement)]
    K[04 Image Worker] <-->|Polling·결과 반영| DB
    M -->|JDBC 집계| DB
```

Measurement는 Web Server나 Worker를 직접 시작하지 않는다. 세 프로세스의 유일한 공유 계약은 HTTP와 MySQL 스키마다.

## 측정 범위와 확대 기준

| 구분 | 범위 | 실행 시점 |
| --- | --- | --- |
| 기본 검증 | HTTP 요청 300건의 접수부터 최종 결과까지 측정 | 실행 예정 |
| 추가 부하 측정 | 300건보다 많은 요청 또는 더 높은 HTTP 등록 동시성으로 측정 | 기본 검증 통과 후 |

기본 검증은 장애를 주입하지 않는 정상 부하 조건에서 다음을 모두 확인한다.

- 요청 300건이 모두 HTTP 202로 접수되고, 고유 Job ID가 300개다. HTTP 실패와 중복 Job ID는 0건이다.
- 제한 시간 안에 300개 Job이 모두 `SUCCEEDED`로 완료된다. `FAILED`, 미종결, 누락, 결과 오연결, 이미지 없는 성공은 각각 0건이다.
- 전체 완료 시간이 기록된다. 정상 부하에서 재시도는 0건을 기대하며, 발생하면 오류 로그와 원인을 확인한 뒤 추가 부하 측정 여부를 판단한다.

추가 측정에서는 먼저 `measurement.job-count`를 늘리고 HTTP 동시성, Worker 동시성, 모의 생성 지연을 유지해 작업 대기와 전체 완료 시간의 변화를 비교한다. 이후 필요하면 총 요청 수를 고정하고 `measurement.request-concurrency`를 늘려 등록 부하를 비교한다. 한 번에 하나의 조건만 바꾸며, 상위 단계의 요청 수·동시성·제한 시간은 기본 검증 결과를 보고 결정한다.

요청 수를 늘릴 때는 Worker 동시성과 모의 생성 지연에 따른 예상 완료 시간을 고려해 `measurement.timeout`을 설정한다. 제한 시간 종료 시 남아 있는 `PENDING`·`RUNNING`은 미종결로 집계하고 누락과 구분한다. 각 실행에서 접수·완료·실패·미종결·누락·재시도·전체 완료 시간을 요약하고, 추가 부하의 변화는 최대 PENDING과 대기 시간의 p95·p99로 확인한다. 결과 파일에는 실행 조건과 모든 지표를 보존한다.

현재 측정 프로그램은 총 요청 수와 HTTP 등록 동시성을 조절한다. 일정한 초당 요청 수를 유지하는 기능은 없다. 모의 이미지 생성 조건에서 얻은 결과이며, 실제 AI 생성 성능이나 장애 복구 능력의 근거로 일반화하지 않는다. 장애 복구 근거는 [05단계](../05-regression-verification/README.md)를 참조한다.

## 설정

`run06Measurement`는 Spring Boot 실행 인자로 다음 설정을 받는다.

| 설정 | 기본값 | 용도 |
| --- | --- | --- |
| `measurement.base-url` | `http://127.0.0.1:8080` | 04 Web Server 주소 |
| `measurement.job-count` | `300` | 등록할 요청 수 |
| `measurement.request-concurrency` | `35` | HTTP 등록 동시성 |
| `measurement.timeout` | `10m` | 등록부터 최종 관찰까지의 제한 시간 |
| `measurement.polling-interval` | `1s` | HTTP·DB 관찰 간격 |
| `measurement.output-directory` | `06-final-300-job-measurement/results` | 결과 루트 경로 |
| `measurement.expected-worker-concurrency` | `8` | 결과에 기록할 비교 조건 |
| `measurement.expected-generator-delay` | `5s` | 결과에 기록할 모의 생성 조건 |

`job-count >= 1`, `request-concurrency >= 1`, `request-concurrency <= job-count`, `timeout > 0`, `polling-interval > 0`을 검증한다. `base-url`에는 인증정보를 포함할 수 없다. 예상 Worker 동시성과 생성 지연은 측정 프로그램이 강제하지 않고 실행 환경 메타데이터로만 기록한다.

Measurement의 DataSource는 표준 `spring.datasource.*` 설정을 사용한다. Web·Worker·Measurement 모두 같은 측정 전용 DB를 바라봐야 한다. 비밀번호와 JDBC URL은 결과 파일에 기록하지 않는다. 🔒

## 측정 지표 📊

| 용어·지표 | 정의 |
| --- | --- |
| HTTP 202 수 | `POST /jobs`가 202를 반환한 요청 수 |
| HTTP 실패 수 | HTTP 202가 아니거나, 202 응답이지만 Job ID·Monster ID를 읽지 못한 요청 수 |
| 고유·중복 Job ID | 202 응답에서 받은 Job ID의 distinct 수와 중복 수 |
| 누락 | HTTP 202로 접수했지만 MySQL에서 해당 Job을 찾지 못한 수 |
| 미종결 | 제한 시간이 끝났을 때 `PENDING` 또는 `RUNNING`인 Job 수 |
| 재시도 Job | `attemptCount >= 2`인 Job 수 |
| 추가 시도 수 | `sum(max(attemptCount - 1, 0))` |
| 결과 오연결 | `SUCCEEDED` Job의 Monster 이미지가 `image:{prompt}`와 다른 수 |
| 이미지 없는 성공 | `SUCCEEDED` Job의 이미지가 비어 있는 수 |
| 최대 PENDING | runId prompt 범위에서 관찰한 `PENDING` 최대 수 |
| 요청·대기·처리 시간 | 각각 HTTP 요청, `startedAt-createdAt`, `finishedAt-startedAt`의 p50·p95·p99 |

전체 완료 시간은 접수 시작부터 모든 접수 Job의 최종 `finishedAt`까지로 계산한다. 제한 시간에 미종결 Job이 남으면 이 값은 비어 있다.

`attemptCount`는 성공적 선점 횟수다. 실제 Generator 호출 횟수나 중복 실행을 완전히 증명하지 않는다. **Exactly Once를 주장하지 않는다.**

## 실행 순서 🚦

1. `technical_writing_measurement` DB를 만들고 04 스키마를 한 번 적용한다. 자동 삭제·초기화는 하지 않는다.

   ```bash
   mysql --host=127.0.0.1 --user=측정사용자 --password technical_writing_measurement \
     < 04-reliability-hardening/src/main/resources/db/04/schema.sql
   ```

2. Web Server와 Image Worker를 별도 터미널에서 같은 측정 DB로 시작한다.

   ```bash
   TECHNICAL_WRITING_DB_URL='jdbc:mysql://127.0.0.1:3306/technical_writing_measurement?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true' \
     ./gradlew run04WebServer

   TECHNICAL_WRITING_DB_URL='jdbc:mysql://127.0.0.1:3306/technical_writing_measurement?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true' \
     ./gradlew run04ImageWorker --args='--db-queue.concurrency=8 --image-generator.delay=5s'
   ```

3. 04 테이블이 비어 있는지 확인한 뒤 Measurement를 별도 터미널에서 실행한다.

   ```bash
   TECHNICAL_WRITING_DB_URL='jdbc:mysql://127.0.0.1:3306/technical_writing_measurement?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true' \
     ./gradlew run06Measurement --args='--measurement.base-url=http://127.0.0.1:8080 --measurement.job-count=300 --measurement.request-concurrency=35 --measurement.timeout=10m --measurement.polling-interval=1s --measurement.expected-worker-concurrency=8 --measurement.expected-generator-delay=5s'
   ```

Measurement는 연결 DB 이름이 정확히 `technical_writing_measurement`인지 확인한다. `queue_monster` 또는 `image_generation_job`에 기존 데이터가 있으면 자동 삭제하지 않고 실행을 중단한다. ⚠️

추가 부하 측정도 기존 결과 파일을 보존하고 위의 빈 테이블 조건을 충족한 독립 실험으로 실행한다. 측정 프로그램이 이전 실행 데이터를 자동 정리하지 않는다.

## 결과 파일 🗂️

성공적으로 실행한 경우에만 아래 경로에 파일을 만든다. 현재 저장소에는 실제 측정 결과 파일이 없다.

```text
06-final-300-job-measurement/results/{runId}/
├── summary.json
├── jobs.csv
├── request-latency.csv
└── environment.json
```

| 파일 | 형식·핵심 필드 |
| --- | --- |
| `summary.json` | runId·관찰 시각·완료 여부, 요청·상태·누락·재시도·오연결 집계, 최대 PENDING, p50·p95·p99 |
| `jobs.csv` | `request_number`, `prompt`, HTTP 결과, Job·Monster ID, 상태·시도·시각, 이미지·기대 이미지, 오연결·누락 여부 |
| `request-latency.csv` | `request_number`, `prompt`, HTTP 상태, Job ID, 요청 시작·종료, 지연 시간, 실패 원인 |
| `environment.json` | runId, 안전한 base URL, DB 이름, 요청·Worker·지연 설정, Java·OS·MySQL 버전 |

DB 비밀번호, 인증정보가 포함된 JDBC URL, 전체 환경변수는 어떤 결과 파일에도 기록하지 않는다.

## 현재 상태

Measurement 프로그램, Gradle 실행 작업, 결과 형식, 외부 의존 없는 단위 테스트는 준비했다. 300건 기본 검증은 실행 예정이며, 통과 후 추가 부하 측정을 수행한다. 두 종류의 실제 측정 결과는 아직 없다. 02·04 성능 비교, 장애 주입, 실제 AI 생성, 04 운영 코드 변경은 비범위다.
