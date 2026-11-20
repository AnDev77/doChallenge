# Learner's High 개발 대조 기록

## 2026-11-20 — CAMT 화면 연결을 위한 Java 목록 계약

- **원본 p. 6:** 데이터 정합성·중복 처리 질문을 화면 진입 여부와 실제 승인 상태를 분리하는 데 적용했다. 원본에 이 프론트 계약이 있는 것은 아니다.
- **우리 문제:** 승인 GET 코드가 있어도 공개 종목 목록의 CAMT `briefing=false` 때문에 웹에서 패널을 볼 수 없었다.
- **선택·검증:** [InstrumentCatalog](../../src/main/java/com/fitmeet/instruments/InstrumentCatalog.java)는 Java 공개 모드일 때 CAMT만 지원으로 표시한다. [HTTP 시험](../../src/test/java/com/fitmeet/briefing/JavaBriefingHttpIntegrationTest.java)은 미승인 상태와 승인 SEC 인용을 확인한다. [한 종목 흐름 시험](../../src/test/java/com/fitmeet/briefing/CamtBriefingFlowIntegrationTest.java)은 합성 원문부터 승인 공개 GET까지의 상태 순서를 확인한다. 실제 원문·모델 생성·사람 검토·외부 브라우저는 미검증이다.


## 2026-11-20 — 비웹 운영자 초안·검토 경로

- **원본 p. 6:** 저장 정합성과 중복 처리에 관한 질문을 검토 시점의 원문 검증과 연결했다. 원본이 사람 검토 도구나 CLI 구조를 제시한 것은 아니다.
- **우리 문제:** DB에는 해시 조건부 승인 메서드가 있어도 실제 운영자는 초안 생성·인용 근거 열람 경로가 없었다.
- **선택·코드:** [운영자 명령](../../src/main/java/com/fitmeet/briefing/operator/BriefingOperatorRunner.java)은 비웹·명시적 액션만 받고, 인용 청크 텍스트·메타데이터를 다시 확인한 뒤 기존 승인 서비스를 호출한다. [시험](../../src/test/java/com/fitmeet/briefing/operator/BriefingOperatorRunnerTest.java)은 승인 전/후 공개 상태와 손상 거절을 확인한다.
- **검증 상태:** 로컬 Gradle 다운로드가 차단돼 원격 Java 회귀 결과를 별도로 확인한다. 서버 접근자 신원과 입력된 검토자 ID의 일치, 사람의 의미 판단, 실제 SEC/Gemini 호출은 검증하지 않았다.


이 문서는 사용자 제공 *Learner's High 서버 3기 세션자료*의 질문을 개발 판단에 어떻게 사용했는지만 기록한다. 페이지별 현재 구현은 [구현 대조표](learners-high-implementation-map.md)에 있고, 이력서·포트폴리오에 쓸 표현은 [별도 근거 문서](../portfolio/java-rag-briefing-evidence.md)에 둔다. 토스의 구현을 동일하게 재현했다는 주장은 하지 않는다.

## 2026-11-19 — 브리핑 모델 공급자 추가

- **원본 p. 13:** “더 싼 모델로 바꿀 수 있을까? 결과 퀄리티는 계속 유지될까?”는 비용과 결과를 함께 비교하라는 질문이다. 원본이 특정 Gemini 모델이나 RAG 알고리즘을 제안하지는 않았다.
- **우리 문제:** OpenAI만 연결된 상태에서 실제 공시 요약 품질/비용을 확인하기 전에 공급자를 확정하기 어려웠다.
- **선택·코드:** [Spring AI 공통 모델 경계](../../src/main/java/com/fitmeet/briefing/generation/SpringAiBriefingModel.java)는 유지하고 [의존성](../../build.gradle)과 [설정](../../src/main/resources/application.yml)에 Google GenAI를 추가했다. `none` 기본값과 JSON/인용 검사는 유지하고 공급자만 `google-genai`로 선택할 수 있게 했다. [공급자 컨텍스트 시험](../../src/test/java/com/fitmeet/briefing/generation/GeminiProviderContextTest.java)은 가짜 키로 자동 설정을 확인하며 외부 호출은 하지 않는다.
- **검증 상태:** PR #33 코드 커밋 `8b542f2`의 Java 전체 시험/JAR·후보 이미지·Compose·Blue/Green 등 일곱 원격 워크플로가 성공했다. 가짜 키 컨텍스트 시험의 모델 선택까지 확인한 것이며 실제 Gemini 호출·품질/비용 비교는 API 키와 동일한 평가 집합이 없어 아직 하지 않았다. 합성 시험을 “무료 Gemini 품질 검증”이라고 부르지 않는다.
- **다음 비교:** 고정한 공개 SEC 원문 30건을 같은 시점·질의로 재생해 유효 JSON/인용, 근거 일치, 한글 가독성, 지연, 토큰 비용을 모델별로 기록한다. 실패·자료 부족도 함께 남기고 유료 호출과 운영 채택은 별도 판단한다.

## 2026-11-19 — Java 승인 결과 공개 응답

- **원본 p. 6·13:** 데이터 정합성과 AI 결과 품질의 질문을 공개 조회에 적용한다.
- **문제:** DB의 승인 초안은 단일 문자열 요약과 숫자 청크 ID인 반면, 웹은 근거별 문자열 ID와 HTTPS 출처를 요구한다.
- **선택:** [공개 변환기](../../src/main/java/com/fitmeet/briefing/JavaBriefingPublication.java)가 저장된 승인 버전을 기존 [GET](../../src/main/java/com/fitmeet/briefing/BriefingController.java)의 계약으로 변환한다. [시험](../../src/test/java/com/fitmeet/briefing/JavaBriefingPublicationTest.java)은 승인 결과와 비공개·잘못된 링크를 다룬다.
- **판정:** 코드와 시험을 PR로 검증한다. 실제 공시·모델·운영 조회의 종단 검증과 부하 수치는 기록되지 않았다.
