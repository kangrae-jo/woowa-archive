# 2026-10-18 300건 기본 측정 실행 기록

**채택:** runId `04a01500-aafe-44be-a619-9c4a1ad2b72b`의 300건 정상 부하 측정은 기본 검증 조건을 모두 통과했다. 수치의 원본은 [summary.json](./summary.json)이며, 실행 조건·지표별 결과·해석은 [06단계의 실제 측정 결과](../../README.md#실제-측정-결과)에서 관리한다.

## 실행 과정

1. 기존 애플리케이션 소스를 Gradle의 `classes` 작업으로 오프라인 컴파일하고 `sourceSets.main.runtimeClasspath`를 추출했다. 측정 실행 전 작업 트리에는 문서 수정만 있었으며, 소스 기준 커밋은 `36bc836a1cfb16ad78961a4ba8747eb687abca92`다.
2. `/private/tmp` 아래 새 데이터 디렉터리로 MySQL 9.2.0 인스턴스를 시작했다. `technical_writing_measurement` DB에 [04 스키마](../../../04-reliability-hardening/src/main/resources/db/04/schema.sql)를 한 번 적용하고, Monster·Job이 각각 0건임을 확인했다. 기존 MySQL 인스턴스와 데이터는 변경하지 않았다.
3. `WebServerApplication`, `ImageWorkerApplication`, `MeasurementApplication`을 각각 별도 Java 프로세스로 실행했다. Web의 주소는 `http://127.0.0.1:51057`이었으며, 세 JVM은 HTTP와 DB만 공유했다.
4. 2026-10-18 12:53:55~12:57:09(KST)에 측정했다. HTTP 등록 동시성 35, Worker 동시성 8, 모의 생성 지연 5초, 관찰 간격 1초, 제한 시간 10분을 사용했다. 정해진 초당 유입률을 유지하는 실행은 아니다.
5. 프로그램 종료 코드 `0`과 결과 파일 생성을 확인한 뒤, CSV의 300개 요청·Job과 백분위수·요약을 독립적으로 대조했다. DB의 최종 행 수·성공 상태·선점 횟수·이미지 연결도 별도로 조회했다.
6. 모든 Job이 완료된 후 Web·Worker에 SIGTERM을 보내 정상 종료하고 임시 MySQL도 종료했다. 이 종료는 측정 후 정리이며, 처리 중 종료·재시작을 통한 장애 복구 검증은 아니다. 모든 프로세스의 종료 상태는 [execution.json](./execution.json)에 남겼다.

실제로 사용한 메인 클래스·인자·프로세스 ID, 하드웨어·MySQL 설정과 49개 소스·설정 파일의 SHA-256은 [execution.json](./execution.json)을 참조한다. classpath는 사용자별 캐시 경로 대신 Gradle의 `sourceSets.main.runtimeClasspath`라는 출처로 표시했다. 같은 애플리케이션을 Gradle 작업으로 실행하는 명령과 DB 준비 순서는 [06단계의 실행 순서](../../README.md#실행-순서-)를 따른다. 재실행 시에는 빈 측정 전용 DB를 준비하고 이번 결과 파일을 보존한다.

## 보존한 근거

| 파일 | 내용 |
| --- | --- |
| [summary.json](./summary.json) | 접수·종결·누락·재시도, 전체 완료 시간, 대기열과 지연 백분위수 |
| [environment.json](./environment.json) | 측정 프로그램이 기록한 Java·OS·MySQL 버전과 측정 설정 |
| [jobs.csv](./jobs.csv) | 요청별 Job·Monster ID, 상태·선점 횟수·시각, 실제 이미지와 기대 이미지 |
| [request-latency.csv](./request-latency.csv) | HTTP 요청별 상태와 응답 시간 |
| [execution.json](./execution.json) | 실행 명령·소스 기준·프로세스·DB 설정·종료 확인 |
| [verification.json](./verification.json) | CSV·요약·백분위수 대조 36개 항목의 통과 결과 |
| [database-verification.json](./database-verification.json) | 최종 DB 행 수·상태·선점 횟수·이미지·참조 연결 대조 |
| [web.log](./web.log) · [worker.log](./worker.log) · [measurement.log](./measurement.log) | 각 JVM의 기동·측정 완료·종료 로그 |
| [progress.log](./progress.log) | 실행 중 약 15초 간격으로 조회한 상태별 Job 수. 최종 집계는 `summary.json` 기준 |

DB 접속 비밀번호와 전체 환경변수는 결과에 저장하지 않았다. 추가 부하, 실제 AI 생성, 장애 주입, 02·04 성능 비교는 이번 실행에 포함하지 않았다.
