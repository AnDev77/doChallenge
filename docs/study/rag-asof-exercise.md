# 실습 1: 과거 시점의 SEC 근거 격리

이 브랜치는 **의도적으로 실패하는 학습용 브랜치**다. `main`에 병합하거나 운영에 배포하지 않는다. 수정 대상은 `EvidenceRetriever.search`의 DB 시각 필터 바인딩이며, 정답 패치는 이 문서에 적지 않았다.

## 먼저 읽기

1. `docs/briefing/java-keyword-retrieval.md`의 0~2절과 `rag_document.accepted_at`, `fetched_at` 의미를 읽는다.
2. `src/main/java/com/fitmeet/briefing/retrieval/EvidenceRetriever.java`의 SQL 물음표 순서와 `args`의 순서를 손으로 대응시킨다.
3. `src/test/java/com/fitmeet/briefing/retrieval/EvidenceRetrieverTest.java`의 `pendingAndUnrelatedOrFutureFilingsNeverBecomeEvidence`를 읽는다.

## 과제

SEC 접수는 `2026-11-17T12:00:00Z`, 수집은 `12:05:00Z`인 문서를 `asOf=12:02:00Z`로 검색하면 **0건**이어야 한다. 현재 브랜치는 검색될 수 있다. 코드를 한 곳만 고쳐 미래 수집 자료가 나오지 않게 만든 뒤, 위 시각의 실패와 `asOf=12:06:00Z`의 성공을 같은 테스트에서 확인한다. `accepted_at` 검사, 최신 버전 숨김, `KEYWORD_READY`, 인용 상한은 유지한다.

실행: `bash ./gradlew test --tests com.fitmeet.briefing.retrieval.EvidenceRetrieverTest --no-daemon`. 수정 전에는 관련 테스트가 실패해야 하고, 수정 후에는 전체 `bash ./gradlew test bootJar --no-daemon`도 통과해야 한다. 이 실습 브랜치의 실패한 CI를 제품 회귀로 혼동하지 않는다.

## 공부 메모

- 질문 1: 접수 시각만 제한하면 어떤 미래 참조 편향이 생기는가?
- 질문 2: SQL 바인딩 순서를 잘못 바꾸면 어느 필터가 무력화되는가?
- 질문 3: 같은 원문에 나중 수집한 `PENDING` 버전이 생기면 과거 기준 시각의 조회는 어떻게 달라져야 하는가?

최근 Spring AI 문서는 `QuestionAnswerAdvisor`와 `RetrievalAugmentationAdvisor`처럼 검색을 모델 호출 앞단에 연결하는 선택지를 제공한다. 우리 구현은 MySQL의 두 시각과 버전 상태를 직접 SQL로 제한하므로, 단순히 벡터 검색 어드바이저로 바꾸면 이 계약을 잃을 수 있다. 이번 실습은 어드바이저를 추가하지 않고 근거 후보의 시점 적격성을 이해하는 단계다. 참고: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html

완료 후 설명할 내용: 수정한 한 줄, 실패→성공 테스트, `asOf`와 두 시각의 관계, 이 변경으로 확인하지 못한 운영 MySQL/SEC 실자료 경계.
