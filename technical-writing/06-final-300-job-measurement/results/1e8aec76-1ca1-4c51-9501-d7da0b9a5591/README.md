# 2026-11-01 1,500건 추가 부하 검증 실행 기록

runId `1e8aec76-1ca1-4c51-9501-d7da0b9a5591`은 1,500건 추가 부하 통과 조건을 모두 충족했다. 원본 수치는 [summary.json](./summary.json), 조건별 판정은 [acceptance.json](./acceptance.json)을 기준으로 한다. 실행 조건과 비교표 및 해석은 [06단계의 추가 측정 결과](../../README.md#1500건-추가-부하-측정-결과)에서 관리한다.

## 실행 과정

1. 측정 도구에 `measurement.request-interval`을 추가하고 Java 21 및 오프라인 Gradle로 측정 패키지의 테스트 12건을 실행했다. 실패, 오류와 건너뜀은 모두 0건이었다. 04 웹 서버와 워커 코드는 변경하지 않았다.
2. `/private/tmp` 아래 새 데이터 디렉터리로 MySQL 9.2.0을 시작했다. 기존 MySQL과 분리된 인스턴스의 `technical_writing_measurement` DB에 04 스키마를 적용하고, Job과 Monster가 각각 0건임을 확인했다.
3. 웹 서버, Worker, Measurement를 각각 별도 Java 프로세스로 실행했다. Worker 동시 처리 상한 8, 모의 생성 지연 5초, HTTP 등록 동시 실행 상한 35, 예정 요청 시작 간격 120ms, 총 요청 수 1,500건과 전체 제한 시간 20분을 지정했다.
4. 2026-11-01 00:21:55~00:37:54(KST)에 측정했다. 실제 요청 시작 시각이 모두 측정 시작 후 180초 구간에 있는지 CSV로 확인했다. 접수 p95 500ms 이하, HTTP 실패와 유실 0건, 시작 후 20분 내 전부 성공을 사전에 정한 조건으로 판정했다.
5. CSV의 1,500개 요청과 Job 및 지연 백분위수를 독립적으로 재집계한 36개 항목이 모두 일치했다. MySQL에서도 최종 행 수, 상태, 선점 횟수, 이미지와 참조 연결을 별도로 조회했다.
6. 측정 프로그램 종료 코드 0과 검증 통과를 확인한 뒤 웹 서버와 Worker 및 임시 MySQL을 종료했다. 이는 측정 후 정리이며, 처리 도중 종료와 재시작을 검증한 과정은 아니다.

## 보존한 근거

| 파일 | 내용 |
| --- | --- |
| [summary.json](./summary.json) | 접수와 종결, 누락, 재시도, 완료 시간 및 지연 백분위수 |
| [environment.json](./environment.json) | 요청 간격을 포함한 측정 설정과 Java, OS, MySQL 버전 |
| [jobs.csv](./jobs.csv) | 요청별 ID, 상태, 선점 횟수, 시각, 실제 이미지와 기대 이미지 |
| [request-latency.csv](./request-latency.csv) | HTTP 상태, 실제 전송 시각과 응답 시간 |
| [acceptance.json](./acceptance.json) | 합의한 통과 조건, 실제 투입 구간과 판정 |
| [verification.json](./verification.json) | CSV 및 요약의 36개 독립 대조와 실제 전송 간격 |
| [database-verification.json](./database-verification.json) | DB 행 수, 상태, 이미지 연결 및 선점 정보 대조 |
| [test-verification.json](./test-verification.json) | 측정 도구 테스트 12건의 실행 명령과 결과 집계 |
| [execution.json](./execution.json) | 실행 인자, 소스 해시, 프로세스 ID, DB 설정과 종료 확인 |
| [verify_results.py](./verify_results.py) | CSV와 요약 재집계 및 통과 기준 판정 스크립트 |
| [web.log](./web.log), [worker.log](./worker.log), [measurement.log](./measurement.log) | 각 JVM의 실행 로그 |
| [progress.log](./progress.log) | 약 15초 간격의 상태별 Job 수. 최종 집계는 summary 기준 |

CSV 검증은 Python 표준 라이브러리만 사용하며 다음 명령으로 재현할 수 있다.

```bash
python3 verify_results.py .
```

기존 300건 측정은 요청을 일괄 전송했고 이번에는 120ms 간격으로 전송했다. 요청 수만 바꾼 통제 실험으로 보지 않는다. 실제 AI 생성, 장애 주입과 최대 처리 용량 탐색은 포함하지 않았다. DB 접속 비밀번호와 전체 환경변수는 결과 파일에 저장하지 않았다.
