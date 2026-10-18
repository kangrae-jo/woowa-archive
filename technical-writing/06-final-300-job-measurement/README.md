# 06. 300건 기본 검증과 추가 부하 측정

## 결론 🧪

**채택:** 04 Web Server, 04 Image Worker, Measurement Application을 별도 JVM으로 실행해 HTTP 요청 300건을 기본 검증으로 측정한다. Measurement는 04의 Java 클래스를 import하지 않고 HTTP와 MySQL만 사용한다.

300건 기본 검증이 통과하면 요청 수 또는 HTTP 동시성을 늘려 추가 부하를 측정한다. 300건은 최대 처리량을 주장하기 위한 수치가 아니라, 완료·실패·미종결·누락·재시도를 확인할 기본 검증 조건이다.

2026-10-18에 로컬 MySQL 9.2.0과 세 개의 독립 JVM에서 300건 기본 측정을 실행했다. HTTP 202 접수와 `SUCCEEDED`는 각각 300건이며 실패·미종결·누락·오연결·재시도는 모두 0건이었다. 전체 완료 시간은 193.639초다. 실행 조건과 원본 파일은 [실제 측정 결과](#실제-측정-결과)에 연결한다. 추가 부하 측정은 아직 실행하지 않았다.

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
| 기본 검증 | HTTP 요청 300건의 접수부터 최종 결과까지 측정 | 2026-10-18 통과. [실제 측정 결과](#실제-측정-결과) |
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

측정 프로그램은 관찰을 마치면 아래 경로에 네 파일을 만든다. 현재 저장소에는 [2026-10-18의 실제 결과](./results/04a01500-aafe-44be-a619-9c4a1ad2b72b/README.md)를 보존했다. 이번 실행에는 프로세스 로그와 실행 환경, CSV·DB 대조 기록도 함께 추가했다.

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

## 실제 측정 결과

**채택:** 2026-10-18 12:53:55~12:57:09(KST)의 정상 부하 측정은 300건 기본 검증 조건을 모두 통과했다. runId는 `04a01500-aafe-44be-a619-9c4a1ad2b72b`다. 아래 수치는 프로그램이 생성한 [summary.json](./results/04a01500-aafe-44be-a619-9c4a1ad2b72b/summary.json)을 기준으로 하며, [jobs.csv](./results/04a01500-aafe-44be-a619-9c4a1ad2b72b/jobs.csv)와 [request-latency.csv](./results/04a01500-aafe-44be-a619-9c4a1ad2b72b/request-latency.csv)로 재집계했다.

### 실행 조건

| 항목 | 실제 설정 |
| --- | --- |
| 실행 환경 | Apple M3, 논리 CPU 8개, 메모리 8 GiB, macOS 15.2 arm64 |
| Java·DB | Amazon Corretto 21.0.5, 임시 MySQL 9.2.0, `REPEATABLE-READ`, UTC |
| 프로세스 | Web·Worker·Measurement 각각 독립 JVM. JVM별 `-Xms128m -Xmx512m` |
| DB 범위 | 새 임시 인스턴스의 `technical_writing_measurement`. 시작 시 04 테이블 각각 0건 |
| DB 연결·메모리 | JVM별 Hikari 상한 5개, MySQL InnoDB buffer pool 64 MiB |
| HTTP 등록 | 총 300건, 동시성 35. 정해진 초당 요청 수를 유지하는 방식은 아님 |
| Worker | 프로세스 1개, 동시 처리 상한 8, 모의 생성 지연 5초 |
| 선점·복구 | 처리 기한 30초, 최대 시도 3회, 재시도 간격 1초, Polling·복구 간격 각각 100ms |
| 측정 | 제한 시간 10분, HTTP·DB 관찰 간격 1초 |

실행에 사용한 메인 클래스와 인자, 프로세스 ID, Git 기준 커밋과 소스 해시는 [execution.json](./results/04a01500-aafe-44be-a619-9c4a1ad2b72b/execution.json)에 기록했다. 이번 측정을 위해 애플리케이션·테스트 코드와 기본 설정 파일을 변경하지 않았다. 상세 실행 절차와 로그는 [해당 실행 기록](./results/04a01500-aafe-44be-a619-9c4a1ad2b72b/README.md)을 참조한다.

### 접수·완료와 결과 연결

| 지표 | 실제 결과 |
| --- | ---: |
| HTTP 202 / 고유 Job ID | 300 / 300건 |
| HTTP 실패 / 중복 Job ID | 0 / 0건 |
| `SUCCEEDED` / `FAILED` | 300 / 0건 |
| `PENDING` / `RUNNING` / 미종결 | 0 / 0 / 0건 |
| 누락 / 오연결 / 이미지 없는 성공 | 0 / 0 / 0건 |
| 재시도 Job / 추가 선점 시도 | 0 / 0건 |
| 관측한 최대 PENDING | 295건 |
| 전체 완료 시간 | 193,639ms (193.639초) |
| 최종 상태 관찰까지의 시간 | 194,127ms (194.127초) |

### 요청·대기·처리 시간

단위는 ms이며 nearest-rank 방식으로 계산했다.

| 지표 | p50 | p95 | p99 |
| --- | ---: | ---: | ---: |
| HTTP 등록 응답 | 20 | 160 | 173 |
| Job 대기 (`started_at - created_at`) | 91,719 | 178,087 | 188,270 |
| Job 처리 (`finished_at - started_at`) | 5,007 | 5,016 | 5,038 |

CSV 300행의 고유 ID·상태·이미지 연결과 백분위수를 다시 계산한 36개 대조 항목은 모두 일치했다. DB에서도 Monster·Job·성공 결과가 각각 300건이고, 선점 횟수 합계는 300회이며, 미해제 token·기한과 결과 대상 누락은 0건임을 확인했다. 상세 판정은 [verification.json](./results/04a01500-aafe-44be-a619-9c4a1ad2b72b/verification.json)과 [database-verification.json](./results/04a01500-aafe-44be-a619-9c4a1ad2b72b/database-verification.json)을 참조한다. 검증 뒤 측정용 세 JVM과 임시 MySQL을 모두 종료했다.

### 해석과 제약

HTTP 등록 응답의 p95는 160ms였고 Job 대기의 p95는 178.087초였다. 300건을 짧은 시간에 접수해도 동시 처리 상한 8과 5초 모의 생성 지연 때문에 뒤의 작업은 DB에서 기다린다. 접수 시간과 최종 결과까지의 대기 시간을 함께 봐야 한다.

이번 결과는 로컬 환경에서 수행한 단일 정상 부하 실행이다. 최대 PENDING 295건은 1초 간격 관찰에서 포착한 값으로, 순간 최대값의 보장은 아니다. HTTP 동시성 35는 실제 사용자 35명이나 초당 35건의 유입을 뜻하지 않는다. 300건 통과를 최대 처리량, 실제 AI 생성 성능, 과거 운영 장애의 해결 근거로 일반화하지 않는다. 재시도 0건은 선점 기록의 결과이며 Generator 호출이 정확히 한 번이었다는 별도 증명은 아니다. 장애 주입·프로세스 재시작 복구와 추가 부하는 이번 실행에 포함하지 않았다.

## 현재 상태

300건 기본 측정과 원본 결과·실행 환경·검증 기록의 저장을 완료했다. 추가 부하 측정은 미실행이다. 02·04 성능 비교, 장애 주입, 실제 AI 생성, 04 운영 코드 변경은 이번 측정의 비범위다.
