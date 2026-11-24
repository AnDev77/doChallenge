# 포트폴리오 근거: Java 공시 RAG 브리핑

기준일: 2026-11-20. 이 문서는 실제 구현/시험과 아직 확인하지 않은 결과를 분리해 포트폴리오 문구를 갱신하는 곳이다. Learner's High 원본 질문과의 기술 비교는 [개발 대조 기록](../references/learners-high-development-log.md)에 따로 기록한다.

## 이번 단계의 사실

| 문제·선택 | 확인할 코드·시험 | 쓸 수 있는 범위 |
|---|---|---|
| 과거 시점에 없던 SEC 공시를 근거로 넣지 않기 | [검색](../../src/main/java/com/fitmeet/briefing/retrieval/EvidenceRetriever.java), [검색·버전 시험](../../src/test/java/com/fitmeet/briefing/retrieval/EvidenceRetrieverTest.java) | SEC 접수·시스템 수집 시각, 같은 accession/파일명의 최신 수집본 상태를 검색에 적용했다. 공식 수정 공시 간 관계까지 해결한 것은 아니다. |
| 손상·중복된 초안 승인 차단 | [청크 준비](../../src/main/java/com/fitmeet/briefing/retrieval/KeywordReadinessService.java), [초안 승인](../../src/main/java/com/fitmeet/briefing/publication/BriefingDraftService.java), [저장·승인 시험](../../src/test/java/com/fitmeet/briefing/publication/BriefingDraftServiceTest.java) | 해시·연속 청크 검사와 트랜잭션·조건부 승인을 구현했다. 의미 검증이나 실제 검토 UI는 아니다. |
| 모델 공급자 선택과 출력 계약 | [Spring AI 어댑터](../../src/main/java/com/fitmeet/briefing/generation/SpringAiBriefingModel.java), [생성](../../src/main/java/com/fitmeet/briefing/generation/BriefingGenerationService.java), [모의 모델 시험](../../src/test/java/com/fitmeet/briefing/generation/BriefingGenerationServiceTest.java), [공급자 선택 시험](../../src/test/java/com/fitmeet/briefing/generation/GeminiProviderContextTest.java), [설정](../../src/main/resources/application.yml) | OpenAI와 Gemini를 설정으로 선택할 수 있는 연결을 추가했다. 기본은 호출하지 않으며 실제 Gemini 결과는 미검증이다. |
| 승인 결과 공개 계약 | [Java 변환기](../../src/main/java/com/fitmeet/briefing/JavaBriefingPublication.java), [조회 컨트롤러](../../src/main/java/com/fitmeet/briefing/BriefingController.java), [계약 시험](../../src/test/java/com/fitmeet/briefing/JavaBriefingPublicationTest.java) | 승인된 DB 요약을 기존 웹 JSON 형식으로 바꾸고 인용 URL을 SEC HTTPS 원문으로 제한하는 코드를 작성했다. 실제 웹 공개·실자료 종단 결과는 이후에 기록한다. |
| 비웹 운영자 초안 실행·근거 확인 | [운영자 명령](../../src/main/java/com/fitmeet/briefing/operator/BriefingOperatorRunner.java), [작업 흐름 시험](../../src/test/java/com/fitmeet/briefing/operator/BriefingOperatorRunnerTest.java) | 수집 이후 Java 모델 초안 호출, 저장된 인용 본문 확인, ID·해시를 지정한 승인을 분리한 명령을 작성했다. 서버 접속자 신원 인증·실자료 호출·운영 사람 검토 완료를 증명하지는 않는다. |
| CAMT 종목 목록과 브리핑 계약 | [종목 목록](../../src/main/java/com/fitmeet/instruments/InstrumentCatalog.java), [HTTP 계약 시험](../../src/test/java/com/fitmeet/briefing/JavaBriefingHttpIntegrationTest.java), [한 종목 흐름](../../src/test/java/com/fitmeet/briefing/CamtBriefingFlowIntegrationTest.java) | Java 공개 모드에서 CAMT만 브리핑 가능으로 알려주고 합성 SEC 근거로 초안·승인 전후 GET을 검증한다. 실제 운영 화면 표시 주장은 배포 후 별도 확인한다. |
| Java 공개 공급자 선택 전달 | [단일 Compose](../../deploy/compose.yaml), [Blue/Green 구성](../../deploy/compose.bluegreen.yaml), [배포 절차](../../deploy/README.md) | 운영 `.env`가 `java`를 선택할 때만 Java 승인 결과 조회를 사용하도록 배포 설정을 추가했다. EC2에서 실제 설정을 전환하거나 실공시 결과를 공개한 증거는 아직 없다. |

## 현재 사용할 수 있는 문구

> SEC 공시를 수집해 원문과 청크의 해시를 보존하고, 접수·수집 시각을 함께 제한해 과거 시점의 근거를 검색하도록 구현했습니다. 생성 초안은 검색된 청크 ID에만 인용할 수 있고, 저장 전 재검색과 해시 확인을 거친 뒤 별도 승인 상태로 관리합니다. Spring AI의 공통 모델 경계로 OpenAI·Gemini 공급자 선택지를 구성했으며, 현재 자동 시험에서는 모델 응답의 형식·인용 오류가 초안 저장으로 이어지지 않는지 검증합니다.

## 결과가 나올 때까지 제외할 표현

- “Gemini로 공시 요약 품질을 검증했다”, “무료로 운영 중이다”, “모델 비용을 절감했다”: 실제 API 호출·비용·품질 비교가 없다.
- “사람이 검토한 브리핑이 사용자 화면에 제공된다”: 비웹 승인 명령과 선택형 Java 공개 GET 코드가 있어도 실자료 검토·운영 웹 연동의 증거가 없다.
- “Kafka 기반 비동기 생성”, “환각을 제거했다”, “금융 예측 정확도를 입증했다”: 해당 구현이나 의미 평가가 없다.

실제 모델 평가 후 같은 고정 자료의 성공/실패 건수, 근거 일치 판정 기준, 토큰·지연, 평가 날짜와 실행 환경을 채워 문구를 수정한다. 측정치가 없으면 개선율을 만들지 않는다.

## 2026-11-24 CAMT 기간 선택 변경

운영자 생성의 명시적 30일 검색과 저장 전 같은 기간 재검색, Java 공개 API의 `30d` 승인 결과·오래된 근거 `stale` 표기를 구현했다. 20일 전 접수·방금 수집한 **합성** 공시에 대한 시험을 추가했으며 원격 CI/실제 운영 결과는 별도로 확인한다. 실제 CAMT 공시를 DB에 수집하거나 Gemini 결과를 검토했다는 표현은 사용하지 않는다.
