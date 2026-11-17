# Review: 공개 브리핑 조회를 Python 내부 서비스와 연결

## 시작 상태와 범위

2026-11-15 KST. Java main `553d113`의 회원·채팅 API를 바탕으로, 웹 [브리핑 #9](https://github.com/AnDev77/shockgraph-web/pull/9)와 [Python 공시 브리핑 #18](https://github.com/AnDev77/ShockGarph_AI/pull/18)을 연결하는 GET 경로를 분리해 구현했다. 기존 Java 배포·관심 저장 초안과 별개이며 외부 운영 서버 설정은 변경하지 않았다.

## 구조와 확인 경로

| 코드 | 책임 |
|---|---|
| [BriefingController.java](src/main/java/com/fitmeet/briefing/BriefingController.java) | 4종목과 7일 범위의 공개 조회. 쓰기 API가 없고 응답은 캐시 금지로 전달한다. |
| [BriefingGateway.java](src/main/java/com/fitmeet/briefing/BriefingGateway.java) | 고정 내부 주소에 서버 전용 토큰을 붙인다. 호출자 쿠키·JWT·Origin을 전달하지 않는다. 리다이렉트, 64KiB 초과, 잘못된 HTTP 상태나 종목은 실패로 변환한다. 접속·읽기 제한과 동시에 최대 8회 호출 한도를 둔다. |
| [SecurityConfig.java](src/main/java/com/fitmeet/common/security/SecurityConfig.java), [AuthRateLimitConfig.java](src/main/java/com/fitmeet/auth/presentation/AuthRateLimitConfig.java) | GET만 비로그인 조회, 기존 허용 Origin으로 CORS 범위를 한정한다. |
| [application.yml](src/main/resources/application.yml) | Java에서 `BRIEFING_UPSTREAM_ORIGIN`과 `BRIEFING_INTERNAL_TOKEN`을 함께 받는다. 두 값이 비어 있으면 미연결 상태를 응답한다. |
| [서버 시험](src/test/java/com/fitmeet/briefing/BriefingGatewayTest.java), [보안·CORS 시험](src/test/java/com/fitmeet/briefing/BriefingHttpIntegrationTest.java) | 로컬 가짜 업스트림에 대한 실제 HTTP, 내부 인증, 외부 Origin·쓰기 차단, 잘못된 응답의 실패 변환. |

흐름: **React → 공개 Java GET → 서버 토큰을 붙인 내부 FastAPI 조회 → 사용자용 JSON**. 기존 Nginx는 /api/를 Java에 연결한다. 별도 Docker 내부 서비스 구성과 승인된 이미지 배포는 다음 단계다. 토큰을 브라우저 빌드 환경에 넣지 않는다. 조회 요청에서는 SEC나 LLM을 호출하지 않는다.

## 검증 및 한계

- 중간: CORS와 인증 규칙을 GET에 한정하고, 업스트림이 미설정이어도 기존 Spring 애플리케이션이 시작되도록 했다. 응답의 원문·내부 메타데이터 및 임의 URL을 요청 경로에 포함하지 않는다.
- 완료: 게이트에 브리핑 시험 5건과 JAR에 컨트롤러·게이트웨이가 포함되는지 확인하는 단계를 추가했다. 로컬 정적 diff 검사는 통과했다. 로컬 Gradle 래퍼의 첫 다운로드가 네트워크에 막혀 Java 시험과 실행 JAR은 원격 결과를 기준으로 판단한다.
- 내용 생성, 운영 Origin/TLS, 운영 토큰 및 Python 서버가 연결된 배포는 별도 검증이다. Python은 현 시점 CAMT 자료 부족으로 응답하므로 가짜 신규 브리핑을 표시하지 않는다. PR은 초안이며 배포·자동 병합은 수행하지 않는다.

## 공통 API·외부 예측 모델 확장 설계

2026-11-16. 친구의 딥러닝 모델을 같은 웹에서 제공하자는 요청을 반영했다. 위의 구현 기록은 보존하고 이번 문서 설계를 별도 기록한다.

- 시작: 세 저장소의 원격 main을 갱신하고 웹 #9·Java #23·Python #18이 미병합 초안임을 확인했다. Java 작업 트리는 깨끗했고 Python build/dist와 웹 node_modules는 수정하지 않았다. Java 열린 PR의 배포·관심 목록·채팅 변경과 이번 설계의 범위를 대조했다.
- 중간: Java 공통 API, Python 내부 API와 RAG/예측 워커, 별도 학습 환경으로 책임을 정의했다. 입력·시각·모델 버전·자원 명세, 영속 접수·중복 방지·재시도·결과 권한, 모델 교체·되돌리기를 설계했다.
- 확인 경로: [현재 방향](docs/current-product-direction.md), [상세 구조·명세·단계](docs/architecture/08-core-api-and-model-extension.md). AGENTS.md에 후속 작업의 시작 경로를 추가했다.
- 작업량: 문서 2개 추가, AGENTS.md와 이 Review.md 갱신. 실행 코드·의존성·운영 설정 변경 없음.
- 오류·불확실성: 첫 GitHub 메타데이터 조회의 인수 이름 오류를 수정해 재조회했다. 기존 계획의 MySQL 제품 저장과 PostgreSQL 연구 저장을 혼동하지 않도록 분리했다. 친구의 목표 변수·입력·CPU/GPU·메모리·학습/평가 범위는 아직 미수령이므로 배포 가능이나 정확도를 확정하지 않았다.
- 검증: 문서 상대 링크 10개, 실제 Java 코드 경로 3개, 코드 블록 짝과 diff 형식 검사를 통과했다. 이번 문서 변경으로 새 예측 시험·부하 시험·운영 시험을 수행했다고 주장하지 않는다. 최종 커밋의 원격 게이트 상태는 PR에서 확인한다.
- 판단: 기존 브리핑 게이트웨이를 확장하는 설계여서 전면 재작성은 필요하지 않다. 확장성은 구조 제안이며 실제 자원 격리·중복 실행 방지·모델 정확성은 후속 구현과 시험으로 입증해야 한다. 첫 출시 일정과 연구 HOLD를 유지한다.

## 토스 요약본 대조와 부하·장애 검증 보완

2026-11-16. 사용자가 보낸 2026-10-27 요약본의 원본/사본을 읽고 현재 구조와 대조했다. 사용자 자료의 요약을 확인했으며 이번에 원본 PDF 전체를 다시 읽었다고 주장하지 않는다.

- 시작: 깨끗한 작업 트리와 원격 #23의 `9f5bde8`을 확인했다. 이전 커밋의 배포 스택·이메일·채팅 게이트는 모두 성공했다. 이 CI는 이번 부하 계획을 실증한 결과가 아니다.
- 중간: 기존 구조의 중복 방지·작업 분리와 달리 구체 부하 조건·계측·개선 채택 기준이 부족함을 확인했다. SimpleBroker는 앱 간 공유되지 않으며 브리핑 HTTP 200도 failed 상태일 수 있는 실제 코드와 연결했다.
- 완료 범위: [부하·장애 검증 계획](docs/architecture/09-load-and-failure-validation.md) 추가, 구조 문서와 AGENTS 연결, 이 리뷰 기록. 4개 문서만 변경했다. 단계별 부하·지표·캐시 버전 경합·작업 복구·교차 앱 채팅·모델 간섭·Blue/Green 및 변경 전후 비교 기준을 작성했다.
- 오류·한계: 이전 요약본의 기술 제한·종목·화면 기술을 현재 결정으로 갱신했고, 4GB 서버에 구성요소를 나누는 것만으로 격리·고가용성이 확보되지 않음을 명시했다. 부하 하네스·Micrometer·공유 방송은 미구현이다. 실제 부하 시험·운영 장애 주입·GPU 시험·LLM 유료 호출은 수행하지 않았다.
- 문서 검증: 상대 링크 12개·실제 코드 경로 5개·Markdown 블록과 diff 형식 검사를 통과했다. 새 커밋의 CI는 별도 확인하며 그 통과를 성능 향상으로 쓰지 않는다.
- 객관적 판단: 설계가 측정 가능한 작업 카드로 구체화됐지만 아직 확장성 실증은 없다. 다음 구현은 기준 계측·합성 공급자·조회 부하 하네스이며, 측정 후 개선 대상을 하나 선택한다. 금융 예측 성능은 HOLD다.

### 참고 문서 보관

사용자가 준 2026-10-27 [토스 자료 적용 요약본](docs/references/toss-learners-high-server-3-adoption-plan.md)을 본문 수정 없이 보관하고, [부하·장애 검증 계획](docs/architecture/09-load-and-failure-validation.md)에서 연결했다. 당시 제안은 참고용이며 최신 종목·스택·부하 시험 결정은 현재 계획이 우선한다. 문서 3개를 변경했으며 실행 코드·설정·원본 PDF는 포함하지 않았다.
## 대표 종목 공통 목록과 관심 저장

2026-11-16 KST. 회원 관심 저장 초안 #16의 원격 커밋 aa01c34를 기준으로 새 분기를 만들었다. 새 목록 변경을 기존 #16 및 브리핑 #23에 섞지 않았다.

- 시작: 웹 최신 계획과 구조 검토를 확인하고 CAMT·SPCX·AVGO를 우선했다. 기존 관심 저장은 QQQ·SPY만 받고 교차 출처 허용 설정도 없었다.
- 변경: Java 공개 GET /api/v1/instruments와 관심 저장이 같은 카탈로그를 사용한다. 기존 5종목의 가시성과 기능 지원을 별도로 반환한다. 새 종목을 자동으로 개인 목록에 저장하지 않는다. 브리핑은 별도 통합이 완료될 때까지 꺼져 있다.
- 확인 코드: src/main/java/com/fitmeet/instruments/InstrumentCatalog.java, InstrumentController.java, src/main/java/com/fitmeet/watchlist/WatchlistService.java, src/main/java/com/fitmeet/auth/presentation/AuthRateLimitConfig.java.
- 검증: 인증·두 회원 분리와 반복 저장, 허용/거부 Origin, 3종목 추가, Docker MySQL 재기동 뒤 AVGO 보존을 시험에 포함했다. Java 21은 설치됐으나 Gradle 배포 파일 다운로드가 네트워크에서 차단되어 로컬 Java 전체 시험·JAR은 실행하지 못했다. diff·Python 구문 확인을 통과했다. Java/Redis/JAR/Compose는 원격 게이트에서 확인한다.
- 한계: 기존 4자리 티커 저장 계약과 V2 스키마를 유지한다. 미국 전체 종목 확장은 별도 고정 ID·마이그레이션·검색 구현이 필요하다. 동시 중복 PUT은 기존 DB 유일 제약이 막으며 성공 응답까지 보장하는 경쟁 시험은 이번 범위에 없다. 운영 설정·가격 공급·친구 모델·실제 배포는 변경하지 않았다.

### main 통합 확인

- 시작: 앞선 PR #10~#23 병합 뒤 #24를 main으로 돌리자 워크플로, 이 리뷰 파일, 공개 조회 보안 설정에서 충돌이 났다.
- 중간: 브리핑·관심 목록 시험 및 JAR 클래스 검사를 모두 남겼고, 브리핑과 대표 종목 GET만 익명 조회하도록 합쳤다. 기존 브리핑 기록과 이번 기능 기록을 함께 보존했다.
- 완료 근거: 로컬 충돌 표식 제거, `git diff --cached --check`, `python3 -m py_compile deploy/smoke_test.py`를 확인했다. 변경 파일 9개이며 최신 main과 합친 새 커밋의 Java·Redis·Docker CI는 별도로 확인해야 한다.
- 한계: 로컬 Gradle 배포 파일 다운로드가 네트워크에서 차단돼 Java 전체 시험·JAR을 로컬에서 다시 만들지 못했다. 운영 MySQL, 실제 사용자 채팅, SMTP, EC2 Blue/Green 전환은 이번 병합 검증에 포함되지 않는다.


## RAG Kafka 경계와 기능 리뷰 규칙

2026-11-17 KST. Python 초안 PR #18의 RAG 작업 Kafka 전달 실험을 Java 제품 경계에 반영한다. 이 문서 변경은 Java 코드, 배포 설정 또는 실행 동작을 바꾸지 않는다.

- 바뀐 파일과 구조: docs/current-product-direction.md는 Kafka를 Python 내부의 CAMT 요약 초안 작업 전달 실험에만 배치한다. Java BriefingGateway와 공개 GET은 검토된 결과만 조회한다. AGENTS.md는 앞으로의 Review.md에 바뀐 파일·구조·선택 이유·추가 기능·시험·한계를 적도록 정한다.
- 이유: Java 회원·채팅 요청과 외부 LLM·공시 원문 경계를 보존하고, Kafka 도입이 실제 브리핑 운영 연결이나 예측 성능 입증으로 오해되지 않게 한다.
- 시작·중간·완료: Python PR #18이 초안이고 Java main의 브리핑 GET이 읽기 전용임을 확인했다. 기존 예측 작업 설계의 브로커 미도입 단계와 이번 RAG 실험을 구분했다. 문서 변경만 제안하며 Java 실행 코드 시험·실 Kafka/LLM 연결·EC2 배포는 수행하지 않았다. 원격 PR 게이트는 새 커밋에서 확인한다.


## Java RAG 소유권 변경 (2026-11-17)

- 변경 파일·구조: AGENTS.md, 현재 제품 방향, 아키텍처 설계의 최상단 결정을 Java RAG·Kafka 담당으로 바꿨다. 하단 과거 Python 설계는 이력임을 명시했다.
- 이유: 사용자가 Java에서 RAG를 사용하기로 결정했다. Python #18은 main에 병합됐으나 Java RAG의 완료 증거가 아니다.
- 추가 기능·흐름: 문서상 Java CAMT 수집 → 검색·LLM 초안 → 검토·조회 → Java Kafka 작업 전달을 확정했다. 실행 코드 변경은 없다.
- 검증·한계: 세 저장소의 main·PR, Java Gateway와 웹 응답 계약을 확인했다. Java RAG/실 Kafka·LLM·배포는 아직 미검증이다.


## Java RAG 라이브러리 호환 기반 (2026-11-17)

- 변경: build.gradle의 Spring Boot 3.3.5를 3.5.16으로 올리고 Spring AI BOM 1.1.8과 spring-ai-model 기본 모듈을 추가했다. Gradle 8.5·Java 21은 Spring Boot 3.5 요구 범위 안에 있어 유지했다.
- 이유: RAG를 Java에서 구현하기 위해 Spring AI 모델 인터페이스를 먼저 연결하고, 회원·채팅·Redis·MySQL·배포 이미지 회귀를 한 번에 확인한다. 외부 LLM 스타터와 API 키 설정은 실제 생성 기능 단계에서 고른다.
- 추가 기능: 브리핑 화면·API의 사용자 동작 변경 없음. 공시 수집·검색·Kafka 소비는 아직 미구현이다.
- 검증: Spring 공식 호환 조합(3.5.x/1.1.x)과 Gradle 요구 범위를 확인했다. PR #26의 Boot 3.5.16·Spring AI 1.1.8 변경 커밋에서 Java 전체 시험 2개, JAR·후보 이미지 검증 2개, Compose smoke, Blue/Green, 스키마 게이트가 모두 성공했다. SMTP 실발송과 후보 운영 기동·배포 작업은 조건부 단계여서 생략됐다. 실제 유료 LLM/실 Kafka·운영 배포는 하지 않았다.

## Java CAMT 공시 수집과 불변 원문 저장 (2026-11-17)

- 작업량·변경 파일: Java `briefing/ingestion` 패키지의 SEC 전송·목록 선택·HTML 청크·원문 파일·DB 저장·일회성 실행기, `V3__rag_source_documents.sql`, 수집 설정, jsoup 의존성, 세 가지 테스트와 [상세 코드 리뷰](docs/briefing/java-camt-ingestion.md)를 추가했다. 기존 공개 GET·회원·채팅 동작은 수정하지 않았다.
- 사용자 요청과 이유: Java가 RAG와 Kafka를 소유한다는 결정에 따라 먼저 근거가 될 공식 공시 버전을 Java/MySQL에 남긴다. LLM 응답의 인용을 감사하려면 URL만이 아니라 실제 원문 바이트의 해시·시각·파서 버전·청크 순서를 함께 보존해야 한다.
- 실행 순서: 명시적으로 활성화한 `CamtIngestionRunner` → `SecClient`의 고정 CIK 목록 → `SecFilingCatalog`의 CAMT·기준 시각·서식 검증 → SEC HTML 다운로드 → `FilingChunker`의 표 행/열 분리 → `RawFilingStore`의 해시 파일 → `FilingDocumentRepository`의 원문 버전 및 청크 DB 저장. 파일 경로와 각 코드의 경계·실행 방법은 상세 리뷰에 썼다.
- 정상·중복·정정·실패: 기본 기동에서 외부 호출 없음. 같은 accession·문서·원문 해시는 DB 유일 키로 중복 제거한다. 정정 공시나 바뀐 원문은 새 버전으로 기록한다. 다른 발행자·불완전한 SEC 배열·미래 문서·잘못된 파일명·리다이렉트·과대 응답·빈 본문은 실패 처리한다. 파일 쓰기 후 DB 실패 시 해시 파일만 남을 수 있으며 재실행으로 회복한다. 청크는 `PENDING`이라 색인·공개되지 않는다.
- 확인한 자료: SEC 실 CAMT 목록의 2026-11-04 6-K accession `0001178913-26-005000`과 HTML 원문 HTTP 200을 읽기 전용으로 확인했다. 11/17 현재 7일 밖의 공시이므로 새 공개 브리핑의 최신 근거로 간주하지 않는다.
- 테스트·한계: 카탈로그의 발행자/열 길이/정정·기간 선택, 표의 단위·숨겨진 텍스트, H2 MySQL 모드에서 V3 적용·원문 두 버전·중복 저장 시험을 추가했다. `git diff --check` 통과. 로컬 Gradle 8.5 다운로드는 네트워크 차단으로 실패해 Java 테스트와 실행 JAR 결과는 PR CI에서 확인한다. 유효한 SEC 연락처·운영 영속 볼륨·실 MySQL이 제공되지 않아 Java 수집기의 실제 쓰기 종단 시험은 미완료다. 검색·LLM·검토·Kafka·공개 조회 전환도 미구현이며 이번 단계의 성공으로 기록하지 않는다.
- PR 게이트 첫 실행: Java 시험 2개와 스키마 소스 게이트가 통과했다. Compose는 앱이 기동하고 JAR을 빌드했지만 기존 `deploy/smoke_test.py`가 최신 Flyway 버전을 2로 고정해 V3 도입을 실패로 판단했다. V2 적용 여부와 컨테이너 재생성 전후의 전체 성공 마이그레이션 집합을 검사하도록 고쳤다. 새 커밋에서 Compose·Blue/Green 재검증한다.
- 최신 PR 게이트: 수정 커밋 `d1e2b7b`에서 Java 전체 시험 2개, JAR 검증 2개, Compose 스모크, Blue/Green, 스키마 게이트가 모두 성공했다. SMTP 실발송·후보 운영 기동·배포는 조건부 건너뜀이다. SEC 제출 목록과 HTML은 읽기 전용으로만 확인했으며 Java 수집기로 실제 SEC→MySQL 쓰기를 검증한 것은 아니다.
