# Learner's High 서버 3기: ShockGraph Java 구현 대조표

기준: 2026-11-19 KST, `doChallenge` main의 코드와 시험. 원본은 사용자가 제공한 Viva Republica, *Learner's High 서버 3기 세션자료* PDF 17쪽이다. [2026-10-27 적용 계획](toss-learners-high-server-3-adoption-plan.md)은 당시 제안이며 이 표는 **현재 Java 구현**을 확인하는 용도다. 원본은 기술 문제와 선택지를 소개한다. 아래 코드는 토스의 코드를 이식하거나 동일한 규모를 재현한 결과가 아니라, 그 질문을 우리 요구사항에 맞춰 푼 사례다.

## 지금 “참고해서 적용했다”고 설명할 수 있는 내용

| 원본의 질문 | 우리 문제·적용한 판단 | 코드·검증 근거 | 정확한 범위 |
|---|---|---|---|
| DB/Storage p. 6: 정합성과 중복 처리 | 같은 SEC 원문의 중복 저장은 DB 유일 제약과 원문 해시로 제어하고, 청크의 순서·해시를 확인한 배치만 검색 준비 상태로 바꾼다. 초안과 인용도 한 트랜잭션으로 묶고 승인은 상태·해시 조건부 갱신으로 중복 승인을 막는다. | [V3](../../src/main/resources/db/migration/V3__rag_source_documents.sql), [수집 저장](../../src/main/java/com/fitmeet/briefing/ingestion/FilingDocumentRepository.java), [준비 검증](../../src/main/java/com/fitmeet/briefing/retrieval/KeywordReadinessService.java), [초안 승인](../../src/main/java/com/fitmeet/briefing/publication/BriefingDraftService.java), [롤백 시험](../../src/test/java/com/fitmeet/briefing/retrieval/EvidenceRetrieverTest.java), [승인 시험](../../src/test/java/com/fitmeet/briefing/publication/BriefingDraftServiceTest.java) | 단일 MySQL의 경계다. 원문 파일과 DB의 이기종 트랜잭션, Kafka 소비의 멱등성·exactly-once는 구현하지 않았다. |
| Cache p. 5: 원자적 처리·정합성 | 종목 채팅의 회원별 분당 게시 제한은 Redis Lua `INCR`+`EXPIRE`로 한 번에 검사한다. 최근 메시지 캐시는 최대 30건·6시간 TTL을 사용하고 실패 시 DB 조회 경로로 돌아간다. | [게시 제한](../../src/main/java/com/fitmeet/financechat/RedisFinancePostLimiter.java), [Redis 통합 시험](../../src/test/java/com/fitmeet/financechat/RedisFinancePostLimiterIntegrationTest.java), [최근 메시지 캐시](../../src/main/java/com/fitmeet/chat/infrastructure/RedisChatRecentMessageCache.java) | 금융 브리핑 캐시나 Redis를 원본 저장소로 쓰는 구조가 아니다. 캐시 hit rate·부하 개선 수치는 아직 없다. |
| Network p. 7 / MSA p. 8: 타임아웃·연쇄 장애 | 공개 브리핑의 기존 Python 조회 경계에 접속/읽기 시간 제한, 동시 호출 8개 제한, 64KiB 응답 제한, 실패 상태 반환을 둔다. | [BriefingGateway](../../src/main/java/com/fitmeet/briefing/BriefingGateway.java), [게이트웨이 시험](../../src/test/java/com/fitmeet/briefing/BriefingGatewayTest.java) | Java RAG 공개 조회 전환은 아직 아니다. circuit breaker, service mesh, 분산 tracing, 운영 부하 측정은 없다. |
| 보안/인증 p. 11: 인증 방식과 호출자 통제 | 회원 인증은 JWT 기반이다. STOMP 연결·구독·전송은 서버에서 확인한 주체와 목적지를 기준으로 제한하고, 브리핑의 내부 토큰은 브라우저 요청에서 전달받지 않는다. | [STOMP 경계](../../src/main/java/com/fitmeet/common/security/ChatStompAuthInterceptor.java), [인증 경계 리뷰](../reviews/websocket-auth-boundary-review.md), [게이트웨이](../../src/main/java/com/fitmeet/briefing/BriefingGateway.java), [HTTP 보안 시험](../../src/test/java/com/fitmeet/briefing/BriefingHttpIntegrationTest.java) | mTLS·서비스 메시 정책·키 교체를 적용했다는 뜻이 아니다. |
| 테스트·마이그레이션 p. 12: 실제 연결과 전환 확인 | Flyway 버전 SQL, H2 MySQL 모드의 RAG 트랜잭션 시험, 격리 Compose에서 MySQL/Redis/SMTP/HTTPS 연결 스모크, Blue/Green 전환 실패 후 공개 경로 재검사, 스키마 게이트를 사용한다. | [V3/V4](../../src/main/resources/db/migration/), [검색 시험](../../src/test/java/com/fitmeet/briefing/retrieval/EvidenceRetrieverTest.java), [Compose 스모크](../../deploy/smoke_test.py), [전환](../../deploy/bluegreen.py), [스키마 게이트](../../deploy/bluegreen_readiness.py), [운영 검증 리뷰](../reviews/disposable-compose-acceptance-review.md) | PDF의 Testcontainers·shadow traffic·CDC를 적용한 것은 아니다. CI 합성 환경 검증이며 운영 MySQL, 실제 무중단 트래픽과 장애 복구 실증은 별도다. |
| AI p. 13: 모델 비용·품질 질문 | 모델 호출 전 근거가 없으면 호출을 막고, 출처 ID/시각/해시를 입력에 포함한다. 모델 출력의 JSON 형태와 인용 ID를 검사하고, 저장 전 근거를 재조회한다. 기본 공급자는 비활성화한다. | [생성 서비스](../../src/main/java/com/fitmeet/briefing/generation/BriefingGenerationService.java), [초안 저장](../../src/main/java/com/fitmeet/briefing/publication/BriefingDraftService.java), [생성 시험](../../src/test/java/com/fitmeet/briefing/generation/BriefingGenerationServiceTest.java), [생성 리뷰](../briefing/java-llm-draft-generation.md) | 원본이 RAG 인용/시점 격리를 제안한 것은 아니다. 이것은 원본의 품질·비용 질문에 대한 **우리의 설계**다. 실제 유료 호출, 문장 의미·숫자 대조, 승인자 인증과 공개 조회는 미검증/미구현이다. |

특히 `EvidenceRetriever.search`는 SEC 접수 시각과 **우리 시스템의 수집 시각**을 함께 제한하고, 같은 accession·파일명의 최신 수집본이 `PENDING`이면 이전 `READY`도 숨긴다([검색 코드](../../src/main/java/com/fitmeet/briefing/retrieval/EvidenceRetriever.java), [상세 설명](../briefing/java-keyword-retrieval.md)). 이는 p. 6의 정합성 질문을 금융 시점 재현에 맞춰 **독자적으로 확장한 판단**이다. SEC의 공식 수정 공시가 새 accession으로 제출된 경우의 옛 공시 숨김은 구현하지 않았다. 키워드 `LIKE` 검색의 512건 제한은 반환 후보 수 제한이지 MySQL 전체 스캔 방지가 아니다.

2026-11-24 추가: [운영자 생성](../../src/main/java/com/fitmeet/briefing/operator/BriefingOperatorRunner.java)과 [초안 재검증](../../src/main/java/com/fitmeet/briefing/publication/BriefingDraftService.java)에 동일한 명시적 기간을 전달하고, [공개 30일 조회](../../src/main/java/com/fitmeet/briefing/JavaBriefingPublication.java)는 근거의 SEC 접수 시각으로 기간과 `stale`을 판정한다. [합성 CAMT 흐름 시험](../../src/test/java/com/fitmeet/briefing/CamtBriefingFlowIntegrationTest.java)을 추가했으나 실제 서버 수집·모델 호출·공개 결과는 아직 확인하지 않았다.

## 아직 적용했다고 말하면 안 되는 내용

- **Kafka p. 4:** Java 브리핑 작업의 실제 producer/consumer, 영속 아웃박스, 재발행 멱등 키, DLQ, 순서·lag·rebalance 운영은 아직 없다. 현재 내부 생성 서비스는 동기 호출용이며 실행기도 없다.
- **Cache p. 5 / 성능 p. 9–10:** 금융 분석 결과 캐시, 원격/로컬 캐시 동기화, 프로파일러·실제 hit rate 및 p95 개선 측정은 없다.
- **MSA p. 8 / 시험 p. 12:** 회로 차단기, 서비스 메시, shadow traffic, CDC, Testcontainers는 없다. 격리 Compose 통과를 실제 운영 사고 대응으로 표현하지 않는다.
- **AI p. 13:** MCP 기반 에이전트 실행 환경이나 자동 운영 에이전트, 실제 모델 품질·비용 비교는 없다. PDF에 없는 출처 해시·RAG 세부 규칙을 토스의 기술로 귀속하지 않는다.

작업별 Learner's High 판단과 포트폴리오용 사실 문구는 각각 [개발 대조 기록](learners-high-development-log.md)과 [포트폴리오 근거](../portfolio/java-rag-briefing-evidence.md)에 분리해 남긴다.

## 개발할 때 갱신하는 법

매 기능 작업의 `Review.md`에 **Learner's High 대조**를 짧게 쓴다: `원본 주제·쪽 → 이번에 맞닥뜨린 문제 → 선택한 코드/시험 → 확인한 결과 → 남은 한계`. 직접 대응이 없으면 그 사실과 이유를 기록한다. 이 표는 **새 코드·시험으로 적용 여부가 달라질 때만** 해당 행을 갱신한다. 출시 전 계획, CI의 합성 검증, 운영 부하·데이터·실 모델 검증을 명확히 구분한다.
