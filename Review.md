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
## Blue/Green 전환 실패 후 공개 경로 복구 확인 (2026-11-17)

- 시작: Java main `a7758b3`의 전환 코드와 CI를 확인했다. 구·신 이미지가 같은 MySQL·Redis를 쓰고 정상 전환 후 수동으로 돌아오는 시험은 이미 있었다. 자동 복귀는 단위 시험으로 설정 파일만 확인했고 공개 API 재검사는 없었다.
- 변경 파일과 구조: [bluegreen.py](deploy/bluegreen.py)는 대기 검사 → 두 프록시 경로 기록·검사·재로드 → 공개 스모크 순서로 실행한다. 새 경로 기록 또는 후속 검사가 실패하면 이전 두 경로를 다시 쓰고 동일한 공개 스모크를 실행한다. [test_bluegreen.py](deploy/test_bluegreen.py)는 복귀 성공, 복귀 후 스모크 실패, 두 경로 중 하나만 기록된 실패를 다룬다.
- 이유와 기능: 설정 파일에 Blue라고 적혀 있어도 사용자가 가입하거나 채팅을 볼 수 있다는 뜻은 아니다. 이전 공개 스모크가 통과한 경우에만 복구를 확인했다고 보고한다. 복구 확인에 실패하면 추가 전환을 중단하고 점검하라는 오류를 내며, 복귀가 확인돼도 실패한 배포는 성공 코드로 반환하지 않는다.
- 중간 검증: [.github/workflows/bluegreen-runtime.yml](.github/workflows/bluegreen-runtime.yml)의 격리 환경에서 Green 공개 스모크 성공 직후 종료 코드 73으로 전환 실패를 주입한다. 복귀 후 같은 스모크가 직전 회원의 재로그인과 QQQ 기록 복구, 새 가입·STOMP, SPY 방 분리를 확인한다. Green에서 공개된 종목 목록이 복귀 후 고정 Blue 이미지에서 401을 반환하는지 추가로 검사해, 실제 라우팅도 확인한다.
- 확인한 오류와 조치: [첫 원격 실행](https://github.com/AnDev77/doChallenge/actions/runs/44033484826)은 자동 복귀에 성공했지만 같은 컨테이너에서 정상 전환 시험까지 연달아 돌리자 합성 가입이 가입 한도에 걸려 429를 반환했다. 정상·실패 시나리오를 각자의 새 Compose 환경으로 나눈다. 각 환경 안에서는 계속 Blue와 Green이 MySQL·Redis를 공유하며 앱 한도는 바꾸지 않았다.
- 작업량과 한계: 전환 코드·단위 시험·격리 CI·이 리뷰 등 4개 파일 변경. 로컬 배포 스크립트 전체 37건과 diff 검사 통과. 로컬 Java 시험·JAR은 Gradle 다운로드 네트워크 오류로 시작하지 못했고 Docker 데몬도 없어 격리 Compose는 PR 게이트에서 확인한다. 실제 EC2·SMTP·도메인/TLS·운영 복귀는 미검증이다. 운영 볼륨·트래픽·비밀 설정은 변경하지 않았다.


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

### 종목별 SEC 발행자 설정으로 수정

- 사용자 지적: 첫 버전에는 CAMT 티커·CIK·6-K/20-F 서식이 `SecClient`, 목록 선택, 파일 URL, DB 저장에 반복 고정돼 있었다. 다음 종목마다 코드를 복사해야 하는 구조였다.
- 변경: `application.yml`의 `issuers` 목록에서 CAMT를 첫 설정값으로 두고 `SecIssuer`가 티커·10자리 CIK·허용 서식을 검증한다. `SecIngestionRunner`와 `FilingIngestionService`가 목록을 순회한다. `SecClient`는 설정 CIK로 SEC 주소를 만들고 `SecFilingCatalog`는 응답의 CIK와 티커를 모두 대조한다. `FilingDocumentRepository`는 해당 발행자 정보를 문서별로 저장한다. 임의 응답 URL을 따라가지 않는다.
- 추가된 기능과 경계: 검증된 다른 발행자 항목을 설정으로 추가해 같은 원문 저장·청크 경로를 재사용할 수 있다. 새 종목의 브리핑 공개, PDF·XBRL 처리, 종목별 인용 검증은 여전히 별도 작업이다. 코드 위치와 설정 예시는 [수집 흐름 설명](docs/briefing/java-camt-ingestion.md)에 정리했다.
- 검증: `application.yml`의 실제 CAMT 설정 바인딩, 서로 다른 CIK·서식·종목을 사용하는 두 발행자 선택, 잘못된 발행자 응답과 위험한 종목명 거절, 같은 accession·해시의 종목별 DB 격리를 시험에 추가했다. 최신 커밋의 원격 CI 결과는 별도로 확인한다.
- 최신 통합 게이트: `560a6e3`(main #27 병합 포함)에서 Java 전체 시험 2개, JAR·후보 이미지 검증 2개, Compose 스모크, 스키마 게이트, Blue/Green 정상 전환과 실패 주입 후 자동 복귀가 모두 성공했다. 후보 운영 기동·실 SMTP·배포는 실행 조건이 없어 건너뛰었다. 실제 SEC→Java→MySQL 원문 저장과 운영 영속 볼륨은 아직 미검증이다.
# Java 공시 키워드 검색 기준선 (2026-11-18)

## 시작 상태, 범위, 코드 위치

- 시작: Java main `97d4248`, SEC 수집 #28 head `2a42be2`에서 별도 브랜치를 만들었다. 이후 #28은 main `2f8704e`로 병합됐다. 기존의 다른 로컬 작업 트리와 미커밋 변경은 건드리지 않았다. 웹 11/16 주간 계획과 Java 11/17 제품 방향을 읽었지만, 그 계획의 11/18 공개 브리핑 목표가 달성됐다는 뜻은 아니다.
- 변경 파일: [SecIngestionRunner](src/main/java/com/fitmeet/briefing/ingestion/SecIngestionRunner.java), [KeywordReadinessService](src/main/java/com/fitmeet/briefing/retrieval/KeywordReadinessService.java), [EvidenceRetriever](src/main/java/com/fitmeet/briefing/retrieval/EvidenceRetriever.java), [검색·롤백 시험](src/test/java/com/fitmeet/briefing/retrieval/EvidenceRetrieverTest.java), [더 자세한 SQL·시각 설명](docs/briefing/java-keyword-retrieval.md), 이 Review.md. 새 마이그레이션·서버·공개 API는 없다.
- 추가 기능은 **내부 근거 후보 검색**이다. 브리핑 생성 코드가 아직 `EvidenceRetriever`를 호출하지 않으며 공개 GET은 `BriefingGateway` → Python 조회를 유지한다. 임베딩·LLM·검토·Kafka는 지금 실행되지 않는다.

## 호출 흐름: 원문 저장부터 검색 후보까지

1. `run-once=true`에서만 `SecIngestionRunner.run`이 `FilingIngestionService.collect`를 호출한다. SEC 제출 목록과 HTML을 검증하고 `FilingChunker`가 본문을 청크로 나눈다.
2. `RawFilingStore`가 원본 바이트를 SHA-256 파일에 보존한다. `FilingDocumentRepository.save`는 MySQL의 `rag_document`와 `rag_chunk`를 **한 DB 트랜잭션**에서 넣는다. 처음에는 `index_status='PENDING'`이다. 원본 파일 저장은 MySQL 트랜잭션 밖이므로 DB 실패 시 파일만 남을 수 있다.
3. 수집이 끝나면 실행기가 `KeywordReadinessService.markPendingReady(100)`을 반복한다. 청크 확인에 성공한 문서만 `KEYWORD_READY`가 된다. 이는 검색 가능 상태이지 벡터 임베딩 완료 상태가 아니다.
4. 다음 생성 단계에서 `EvidenceRetriever.search(symbol, query, asOf, lookbackDays, topK)`가 호출되면, 이번 단계의 SQL은 종목·시각·최신 원문 버전을 먼저 제한하고 최대 512개 키워드 일치 청크를 가져온다. Java가 일치 단어 수 등으로 정렬해 최대 8개 근거를 반환한다. 현재 이 마지막 호출자는 미구현이다.

## ① 시점 격리와 버전 숨김: 어떤 행이 왜 빠지는가

`accepted_at`은 SEC에 제출된 시각, `fetched_at`은 우리 DB에 수집된 시각이다. 예를 들어 17일 23:00 UTC에 접수됐지만 18일 01:00에 수집했다면 `asOf=17일 23:30` 검색에서는 **접수 시각 조건은 통과해도 수집 시각 조건에서 제외**된다. `accepted_at BETWEEN asOf-lookback AND asOf`와 `fetched_at <= asOf`를 둘 다 확인해야 우리 시스템의 과거 기록에 아직 없던 문서를 소급해서 쓰지 않는다. 이 조건은 시장 전체의 정보 이용 가능 시각까지 증명하지는 않는다.

동일 `symbol + accession + document_name`의 원문 바이트가 달라지면 `raw_sha256`이 다른 새 문서 행이다. SQL의 `NOT EXISTS`는 `asOf`까지 수집된 더 최신 행이 있으면 구버전을 제외한다. **최신 행의 `index_status`는 조건에 넣지 않는다.** 따라서 구버전은 `KEYWORD_READY`, 최신은 `PENDING`인 순간에도 구버전은 숨겨진다. 새 버전 준비 동안 근거가 비는 대신 교체된 원문을 현재 근거로 쓰지 않는 선택이다. 과거 `asOf`가 새 버전의 `fetched_at` 이전이면 당시 구버전은 계속 검색될 수 있다.

여기서 말하는 새 버전은 **같은 accession·파일명에서 원문 바이트가 바뀐 사례**다. SEC의 공식 `6-K/A` 수정 공시는 보통 **새 accession**이므로 이 SQL이 앞선 제출을 자동으로 숨기지 않는다. 공식 수정 관계 탐지와 대체 정책은 아직 없다. 반환한 `documentId`, `chunkId`, URL·해시·시각은 버전 추적에 쓰지만 **근거와 문장 사이의 의미 검증이나 LLM 환각 방지는 아직 구현하지 않았다.**

## ② 무결성 검사와 트랜잭션: 실패가 어디까지 되돌아가는가

`markPendingReady`는 `PENDING` 최대 100건의 ID를 읽고 문서마다 `rag_chunk`를 `ordinal_no` 순서로 가져온다. `1~1,000개`, `0부터 빈틈없는 순서`, `공백 아닌 본문`, `1,500자 이하`, `SHA-256(UTF-8 본문) == text_sha256`을 검사한 뒤에만 상태를 바꾼다. **저장된 원래 길이와 비교하는 검사는 없다.** 길이는 1,500자 상한만 확인한다.

`@Transactional`은 Spring 빈을 실행기에서 호출하는 **각 `markPendingReady(100)` 배치**에 적용된다. 첫 문서가 준비돼도 두 번째 문서에서 손상이 발견돼 `IllegalStateException`이 나면 해당 배치의 첫 상태 변경도 롤백된다. 이전에 성공한 배치, 수집기의 별도 `save` 트랜잭션, 파일 시스템 저장은 롤백 대상이 아니다. 재실행은 `PENDING` 문서를 다시 검사하지만 **손상을 자동 복구하지는 않는다.** 계속 실패하면 운영자가 원인을 조사해야 한다.

## ③ 후보 512건과 순위: 부하에 대한 정확한 주장

검색어의 문자·숫자 단어를 최대 8개 추출해 `LOWER(c.section_text) LIKE '%word%'`의 **바인딩 인자**로 넣는다. SQL이 종목·기간·버전 조건과 OR 키워드 일치를 평가하고 **최근 접수순으로 정렬한 결과 중 최대 512행을 Java에 반환**한다. Java는 그 후보의 *서로 다른 검색 단어 일치 개수* → 접수 시각 → 문서 ID → 청크 순서로 정렬한 뒤 `topK`(최대 8)를 선택한다.

`LIMIT 512`는 결과 전송과 Java 정렬 건수의 상한이다. `%word%`와 `LOWER(column)`의 **MySQL 전체 스캔을 막거나 DB 쿼리 비용을 고정하지 않는다.** 최신 청크가 512개를 채우면 오래됐지만 관련성 높은 청크는 아예 순위 계산에 못 들어온다. 부분 문자열 매칭·영문 공시와 한국어 질문의 표현 차이도 해결하지 못한다. 규모와 검색 품질은 실제 MySQL 쿼리 플랜·지연과 고정 평가 세트로 측정한 뒤 인덱스/임베딩과 비교한다.

## 검증 결과와 남은 위험

- V3 스키마를 H2 MySQL 모드에 적용한 시험에 종목 분리, 접수·수집 시간 경계, 같은 파일의 두 원문 버전과 최신 `PENDING` 숨김, 손상 해시, 순위를 넣었다. 이번 수정에서는 **Spring 트랜잭션 프록시를 실제로 띄워** 첫 문서 상태 업데이트 뒤 다음 문서 검사 실패 시 두 상태가 `PENDING`인 사례를 추가했다.
- 로컬 `bash gradlew test bootJar --no-daemon`은 Gradle 8.5 배포 파일을 받지 못해 테스트 자체가 시작되지 않았다. `git diff --check`는 통과했다. PR #29의 코드 커밋 `0bdbdc6`에서 Java 21 전체 시험과 실행 JAR 검증 2개, Compose 스모크, Blue/Green 정상·복귀, 스키마 게이트, 이메일 가입 검사가 모두 성공했다. `chat-quality.yml`의 `test` 작업은 `gradlew test bootJar`를 실행하므로 위 롤백 시험도 자동 시험 집합에 포함된다. 후보 이미지 게시·운영 기동·실 SMTP는 조건부 작업으로 건너뛰었다. **이번 기록 커밋의 CI는 별도 확인한다.**
- 실 MySQL의 쿼리 플랜·지연, 실제 SEC→MySQL 수집, 원본 파일 재확인, 30건 검색 평가, 임베딩 비교, LLM 호출·출처 의미 검증, 검토·공개 결과, Kafka와 운영 배포는 아직 미검증/미구현이다. 다음 기능은 검색 기준선의 CI 검증 후 동일 문서에서 임베딩과 비교하고 근거 ID에 묶인 초안·사람 검토를 만드는 것이다. 모델·키·호출 예산이 없으면 유료 LLM 실제 호출은 완료로 세지 않는다.

# Java 근거 스냅샷과 초안 승인 경계 (2026-11-18)

## 시작·변경 파일·추가 기능

- 시작 기준: 원격 main `db9589b`에 #29 검색 PR이 병합된 상태이고 열린 Java PR이 없음을 확인했다. 기존 작업 트리는 그대로 두고 main에서 `feat/java-briefing-publication`을 만들었다. 최신 웹 주간 계획과 Java 제품 방향을 읽었다.
- 변경 파일: [V4 마이그레이션](src/main/resources/db/migration/V4__briefing_draft_review.sql), [BriefingDraftService](src/main/java/com/fitmeet/briefing/publication/BriefingDraftService.java), [통합 성격의 저장·승인 시험](src/test/java/com/fitmeet/briefing/publication/BriefingDraftServiceTest.java), [코드 순서·경계 상세](docs/briefing/java-draft-review.md), 이 Review.md.
- 추가 기능: 내부 생성기가 앞으로 사용할 수 있는 **검색된 근거에 묶인 초안 저장**과 **해시를 확인하는 사람 승인**. 공개 GET은 아직 `BriefingGateway` → Python이며 Java 초안 서비스에 연결되지 않았다. 인증된 리뷰 API·실제 LLM·Kafka도 추가하지 않았다.

## 실제로 호출할 때의 순서와 설계 이유

1. 미래 생성기가 종목·검색어·기준 시각·요약문·인용할 청크 ID 목록을 `create`에 넘긴다. 아직 이 메서드를 호출하는 운영 생성기는 없다.
2. `EvidenceRetriever.search(..., 7, 8)`가 내부에서 실행된다. 종목/시점/버전과 `KEYWORD_READY` 조건을 이미 통과한 이번 검색 결과의 ID만 인용으로 허용한다. 예를 들어 `rag_chunk`에 실재해도 검색 기간 밖이거나 새 `PENDING` 버전 때문에 숨겨진 구버전 청크라면 초안 INSERT 전에 거부한다. 단순 FK만 쓰면 이런 잘못된 인용을 걸러낼 수 없기 때문이다.
3. MySQL `DATETIME(6)`와 일치시키려고 `asOf`를 마이크로초로 정규화하고, 청크 ID 순으로 출처를 고정한다. 종목·시각·요약·각 청크 ID/문서 ID/텍스트 해시/원문 해시/URL/두 시각을 길이 접두사와 함께 SHA-256에 넣어 초안 해시를 만든다. `briefing_draft`와 `briefing_draft_evidence`는 하나의 DB 트랜잭션으로 INSERT한다. FK는 원본 청크와의 연결을, 스냅샷 필드는 그때 검토한 버전의 식별을 맡는다.
4. `approve(id, expectedSha256, reviewer)`는 저장된 요약과 출처를 다시 읽고 해시를 재계산한다. 검토자가 본 해시와 세 값이 같아야 `status='DRAFT' AND content_sha256=?` 조건부 UPDATE를 수행한다. 검토 전 또는 중에 내용이 바뀌었거나 누군가 먼저 승인했다면 실패한다. `latestApproved`도 해시를 다시 확인하고 승인된 행만 돌려준다. **해시가 맞아도 요약의 사실성이나 인용 문장의 의미가 맞는지는 별개**이며 사람이 실제 근거를 보고 검토할 UI·권한은 후속이다.

## 실패·중복·트랜잭션과 검증 상태

- 잘못된 인용/중복 ID·빈 요약은 초안을 남기지 않는다. 초안과 인용 행의 INSERT 중 예외가 나면 해당 서비스 트랜잭션은 롤백된다. SEC 원본 저장이나 다른 배치는 대상이 아니다. 재승인 요청은 DRAFT 상태 검사에서 거절된다. 원본 버전이 뒤늦게 추가되어도 이 초안은 선택 당시의 출처 ID·해시를 보유한다.
- H2 MySQL 모드에서 V3와 V4를 순서대로 적용하고 Spring 트랜잭션 프록시로 PENDING 거부→READY 인용→검토 전 숨김→정확한 해시 승인→재승인 거부와 내용·출처 변조 거부를 검증하는 테스트를 작성했다. 로컬 Gradle 8.5 배포 파일을 다운로드할 수 없어 **아직 실행 성공으로 세지 않는다**. `git diff --check`는 통과했다. 원격 CI와 실 MySQL 마이그레이션 확인 결과는 PR의 커밋별로 이어서 기록한다.
- 인증/권한 있는 검토 진입점, 모델 공급자·키·예산, 구조화 출력/수치 검증, 30건 검색 평가, 실제 문장 대조, 공개 GET 전환, Kafka, 실제 운영 배포는 미구현/미검증이다. 브리핑을 `ready`로 표시하는 사용자 기능을 이번 변경으로 주장하지 않는다. 다음에는 실제 모델의 제한 호출과 초안 포맷·근거 검증을 연결하고, 검토 권한/화면 및 공개 응답 계약을 별도 인수한다.
- 첫 원격 커밋 `8a66209`에서 Java 시험/JAR 게이트가 실패했다. `BriefingDraftService`에 공개 생성자와 시험용 `Clock` 생성자가 함께 있어 Spring이 무인자 생성자를 찾다가 `NoSuchMethodException`이 난 것이 원인이다. 사용하지 않는 두 번째 생성자를 제거해 단일 주입 생성자로 수정했다. 첫 CI의 V4 스키마 게이트는 통과했지만 전체 Java 통과로 해석하지 않는다. 수정 커밋의 원격 게이트를 재확인한다.
- 수정 후 코드가 포함된 `57ffd32` 원격 CI에서는 Java 21 전체 시험·실행 JAR, 후보 이미지 검증, Compose/배포 구성 스모크, Blue/Green 전환·복귀, 스키마 게이트, 채팅·이메일 가입 검사가 모두 성공했다. 이는 합성/CI 환경 검증이며 실 MySQL의 운영 마이그레이션, 실제 SEC 수집, 실 모델 호출, 검토자 인증 및 운영 배포 완료를 뜻하지 않는다. 이 결과를 기록한 문서 커밋의 게이트는 별도로 확인한다.

# Java 근거 기반 LLM 초안 생성 (2026-11-18)

## 시작·파일·새 기능

- 시작 상태: #30은 main `334b6aa`에 병합됐고 열린 Java PR은 없었다. `feat/java-briefing-generation`은 그 main에서 시작했다. 웹 주간 계획과 Java 제품 방향의 Java RAG·사람 검토 경계를 유지한다.
- 바뀐 파일: `build.gradle`과 `application.yml`의 Spring AI OpenAI 스타터·기본 비활성 설정, [모델 포트](src/main/java/com/fitmeet/briefing/generation/BriefingModel.java), [Spring AI 어댑터](src/main/java/com/fitmeet/briefing/generation/SpringAiBriefingModel.java), [생성 흐름](src/main/java/com/fitmeet/briefing/generation/BriefingGenerationService.java), [생성 시험](src/test/java/com/fitmeet/briefing/generation/BriefingGenerationServiceTest.java), [어댑터 시험](src/test/java/com/fitmeet/briefing/generation/SpringAiBriefingModelTest.java), [상세 코드 순서](docs/briefing/java-llm-draft-generation.md), 이 Review.md. 마이그레이션이나 공개 API는 바꾸지 않는다.
- 추가 기능: 내부 호출자가 종목·질의·시점을 주면 실제 Spring AI `ChatModel`로 한 번 요약을 생성할 수 있는 경로다. 기본 앱에는 모델이 없어 호출하면 명시적으로 실패한다. 생성 작업 실행기·검토 UI·공개 조회·Kafka는 아직 연결되지 않아 사용자가 보는 동작 변화는 없다.

## 코드 순서, 설계 이유, 실패 경로

1. `BriefingGenerationService.generate`가 시각을 마이크로초로 정규화하고 `EvidenceRetriever.search(..., 7, 8)`로 최대 8개 근거를 고른다. 0건이면 비용이 드는 모델을 호출하지 않는다. 이 검색은 SEC 접수와 우리 수집 시각, 최신 원문 버전, `KEYWORD_READY` 조건을 따르는 이전 기준선이다.
2. JSON으로 직렬화한 근거 ID·두 해시·접수/수집 시각·URL·본문을 사용자 메시지 데이터로 보내고, 시스템 메시지에서 이 내용의 명령을 무시하고 근거만 쓰도록 지시한다. `SpringAiBriefingModel`은 `ChatModel`이 설정된 경우에만 모델을 호출한다. 호출을 DB 트랜잭션 밖에 놓아 느린 외부 응답 동안 DB 변경 트랜잭션을 열어두지 않는다. 응답 유실/실패 시 공급자 비용이 발생할 수 있으며 DB 롤백으로 이를 되돌릴 수 없다.
3. 응답은 JSON 객체 두 키(`summary`, `citation_ids`)로 제한한다. 중복 키·추가 필드·뒤따르는 텍스트·없는 근거 ID·중복 인용·빈 인용·숫자 포함 문장을 거부하고 초안 INSERT를 하지 않는다. 숫자 제한은 원문 수치/기간/단위를 검증할 코드가 없는 동안 적용한 좁은 정책이다. 인용 ID가 맞아도 문장 의미가 근거와 일치하는지는 아직 사람 검토와 평가가 필요하다.
4. 통과하면 `BriefingDraftService.create`가 같은 시각·검색어로 다시 조회해 모델이 받은 청크가 여전히 적격인지 확인한다. 모델 호출 중 새 `PENDING` 원문이 기존 검색 버전을 숨겼다면 인용이 탈락하고 저장되지 않는다. 재검색 후 V4 초안·근거 INSERT만 한 트랜잭션이다. 저장 성공 상태는 `DRAFT`이지 사용자용 `ready`가 아니다.

## 실제 검증·한계·다음 일

- H2 MySQL 모드 V3/V4와 가짜 모델 출력 시험에 정상 초안·미승인 비공개, 0건 근거의 모델 호출 회피, 가짜/중복 인용, 중복 JSON 키·추가 필드·뒤따르는 텍스트·숫자 등 잘못된 출력 거부, 모델 호출 도중 같은 원문 버전의 새 `PENDING` 수집 시 저장 차단을 추가했다. 별도 Spring AI 어댑터 시험은 역할별 메시지와 모델 미설정 오류를 다룬다. 이 기록 시점 로컬 Gradle 8.5 다운로드는 네트워크 제한으로 실행할 수 없어 원격 PR CI의 전체 시험/JAR 결과를 별도로 확인해야 한다.
- `BRIEFING_CHAT_PROVIDER=none`이 기본이다. 모델을 활성화하려면 키·계정과 비용 한도를 준비한 별도 실행 환경이 필요하다. 실제 유료 호출, 내용 의미·한글 품질·숫자 대조, 30건 고정 근거 평가, 실 MySQL/SEC 종단, 인증된 검토·공개, Kafka, 운영 배포는 이 PR에서 검증하지 않았다. 다음 일은 제한된 실제 모델 호출과 고정 근거 평가, 검토자 인증·공개 조회 연결이다. Kafka는 직접 생성 경로가 검증된 뒤 붙인다.
- 첫 PR CI에서 기존 HTTP/채팅 통합 시험의 앱 컨텍스트 기동이 `BeanCreationException`/`IllegalArgumentException`으로 실패했다. OpenAI 스타터의 채팅·임베딩 외 이미지/오디오/모더레이션 자동 설정도 키 없는 환경에서 활성화될 수 있으므로 해당 모델을 `none`으로 명시해 재검증한다. 실패 상태를 성공으로 기록하지 않는다.
