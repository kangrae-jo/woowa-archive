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
| [04](./04-reliability-hardening/README.md) | 상태·선점·타임아웃·재시도·멱등성 | 보류 |
| [05](./05-regression-verification/README.md) | 동일 실패 테스트 회귀 검증 | 보류 |
| [06](./06-final-300-job-measurement/README.md) | 현재 구현에 300개 작업 입력 후 측정 | 보류 |

## 독립 실행

`technical-writing`은 Java 21, Spring JDBC, H2, JUnit 5로 구성한 독립 Gradle 프로젝트다. `refactoring-practice`의 `sourceSets`에 의존하지 않는다.

```bash
cd technical-writing
./gradlew test
./gradlew failureTest
```

- `test`: `failure-reproduction` 태그를 제외한 정상 테스트 5개 실행.
- `failureTest`: `failure-reproduction` 태그가 붙은 RED 테스트 5개만 실행. 현재 단계의 기대 종료 코드는 `1`이다.

## 현재 범위

1~3단계만 완료했다. 작업 상태와 원자적 선점 등 실제 해결 코드는 4단계 이후 범위다. 300개 작업 측정은 6단계 범위다.
