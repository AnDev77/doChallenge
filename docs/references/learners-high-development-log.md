# Learner's High 개발 대조 기록

이 문서는 사용자 제공 *Learner's High 서버 3기 세션자료*의 질문을 개발 판단에 어떻게 사용했는지만 기록한다. 페이지별 현재 구현은 [구현 대조표](learners-high-implementation-map.md)에 있고, 이력서·포트폴리오에 쓸 표현은 [별도 근거 문서](../portfolio/java-rag-briefing-evidence.md)에 둔다. 토스의 구현을 동일하게 재현했다는 주장은 하지 않는다.

## 2026-11-19 — 브리핑 모델 공급자 추가

- **원본 p. 13:** “더 싼 모델로 바꿀 수 있을까? 결과 퀄리티는 계속 유지될까?”는 비용과 결과를 함께 비교하라는 질문이다. 원본이 특정 Gemini 모델이나 RAG 알고리즘을 제안하지는 않았다.
- **우리 문제:** OpenAI만 연결된 상태에서 실제 공시 요약 품질/비용을 확인하기 전에 공급자를 확정하기 어려웠다.
- **선택·코드:** [Spring AI 공통 모델 경계](../../src/main/java/com/fitmeet/briefing/generation/SpringAiBriefingModel.java)는 유지하고 [의존성](../../build.gradle)과 [설정](../../src/main/resources/application.yml)에 Google GenAI를 추가했다. `none` 기본값과 JSON/인용 검사는 유지하고 공급자만 `google-genai`로 선택할 수 있게 했다. [공급자 컨텍스트 시험](../../src/test/java/com/fitmeet/briefing/generation/GeminiProviderContextTest.java)은 가짜 키로 자동 설정을 확인하며 외부 호출은 하지 않는다.
- **검증 상태:** PR #33 코드 커밋 `8b542f2`의 Java 전체 시험/JAR·후보 이미지·Compose·Blue/Green 등 일곱 원격 워크플로가 성공했다. 가짜 키 컨텍스트 시험의 모델 선택까지 확인한 것이며 실제 Gemini 호출·품질/비용 비교는 API 키와 동일한 평가 집합이 없어 아직 하지 않았다. 합성 시험을 “무료 Gemini 품질 검증”이라고 부르지 않는다.
- **다음 비교:** 고정한 공개 SEC 원문 30건을 같은 시점·질의로 재생해 유효 JSON/인용, 근거 일치, 한글 가독성, 지연, 토큰 비용을 모델별로 기록한다. 실패·자료 부족도 함께 남기고 유료 호출과 운영 채택은 별도 판단한다.
