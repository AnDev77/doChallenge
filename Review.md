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
