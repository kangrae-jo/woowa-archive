# DB Polling 재구성과 신뢰성 검증 기록

과거 커켓몬의 이미지 생성 구조를 현재 코드로 재구성하고, 실패를 먼저 증명한 뒤 신뢰성을 단계적으로 보강한다.

## 용어

- **2025년 당시 안정화**: 이미지 생성 대상을 `Prompt` 테이블로 분리하고, 연속 요청과 생성 개수를 제한한 대응.
- **현재 신뢰성 개선**: 작업 상태, 원자적 선점, 처리 타임아웃 복구, 재시도, 멱등한 결과 반영을 추가하는 작업.

두 표현은 서로 다른 시점과 범위를 가리킨다. 이후 문서에서도 이 구분을 유지한다.

## 작업 흐름

1. 과거 초기 구조와 35명·약 300건 요청에서 겪은 불안정 경험을 기록한다.
2. `Prompt` 테이블과 요청 제한을 이용한 2025년 당시 안정화를 정리한다.
3. 당시 작업 단위 측정 자료가 없다는 한계를 명시한다.
4. 당시 안정화 이후 구조와 유사한 최소 DB Polling 구조를 현재 코드로 재구성한다.
5. 현재 재구성 코드에서 잠재적 신뢰성 문제 5개를 RED 테스트로 재현한다.
6. 4단계부터 현재 신뢰성 개선을 진행한다.

## 단계별 문서

| 단계 | 내용 | 상태 |
| --- | --- | --- |
| [01](./01-past-structure-and-limitations/README.md) | 과거 구조와 기록의 한계 | 완료 |
| [02](./02-minimal-db-polling/README.md) | 최소 DB Polling 재구성 | 완료 |
| [03](./03-failure-reproduction/README.md) | 잠재적 신뢰성 문제 5개 재현 | 완료 |
| [04](./04-reliability-hardening/README.md) | DB Job Queue 신뢰성 개선과 기본 검증 | 완료 |
| [05](./05-regression-verification/README.md) | 동일 실패 테스트 회귀 검증 | 보류 |
| [06](./06-final-300-job-measurement/README.md) | 현재 구현에 300개 작업 입력 후 측정 | 보류 |

## 독립 실행

`technical-writing`은 Java 21, Spring JDBC, H2, JUnit 5로 구성한 독립 Gradle 프로젝트다. `refactoring-practice`의 `sourceSets`에 의존하지 않는다.

```bash
cd technical-writing
./gradlew test --rerun-tasks
./gradlew failureTest --rerun-tasks
```

- `test`: 기존 정상 테스트 5개와 04단계 기본 테스트 23개 실행. 총 28개 성공.
- `failureTest`: 02단계 기준 구현을 대상으로 03단계 RED 테스트 5개만 실행. 의도한 assertion에서 5개 실패하며 기대 종료 코드는 `1`이다.

02·03단계 소스와 실패 재현 의미를 보존한다. 04단계는 별도 `com.kng0501.dbqueue` 패키지와 스키마를 사용한다. 01~03 문서는 각 단계의 기록이며, 전체 빌드의 현재 테스트 구성은 위 명령을 기준으로 한다.

## 현재 범위

1~4단계 구현과 기본 검증을 완료했다. 03단계 RED 테스트를 새 구현에 이식하는 전체 회귀 검증은 05단계에 보류한다. 현재 구현에 300개 작업을 입력하는 측정은 06단계에 보류한다.
