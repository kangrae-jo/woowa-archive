# ③ 해 본 일: 무엇을 해 봤나

작성 상태: 본문 작성 대기. 작성 범위는 [문서 작성 계획서](../WRITING_PLAN.md)를 따른다.

## 본문에서 설명할 질문

- 별도 Web·Worker JVM과 MySQL만으로 최소 처리 흐름을 어떻게 구성했는가?
- 다섯 잠재적 실패는 어느 경계에서 재현되며, 각각 어떤 구현 규칙으로 대응하는가?
- 조건부 UPDATE, 처리 기한, 재시도, claimToken은 함께 어떤 처리 규칙을 만드는가?
- 모의 이미지 생성과 Worker 무응답 상태 재현으로 검증할 수 있는 범위는 어디까지인가?

## 참조할 구현과 검증

- [02. 최소 DB Polling 구조](../02-minimal-db-polling/README.md).
- [03. 잠재적 신뢰성 문제 재현](../03-failure-reproduction/README.md).
- [04. DB Job Queue 신뢰성 개선](../04-reliability-hardening/README.md).
- [05. 신뢰성 회귀 검증](../05-regression-verification/README.md).

본문에 판단을 설명하는 코드만 발췌하고 전체 구현은 링크로 참조한다. Worker 무응답 상태의 복구 규칙과 실제 JVM 종료·재시작 검증은 구분한다.

시각 자료: 최종 구조, 재시도를 포함한 상태 전이도, 오래된 claimToken의 결과를 거부하는 시퀀스.

다음: [④ 달라진 점과 근거](./04-results.md).
