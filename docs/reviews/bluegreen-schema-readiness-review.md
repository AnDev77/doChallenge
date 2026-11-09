# Blue/Green 전환 전 MySQL 스키마 게이트

## 시작 근거 (2026-11-09)

- doChallenge main과 최신 초안 PR #10을 확인했다. Docker Compose의 Java·MySQL·Redis·Nginx 및 합성 가입·채팅 인수 시험은 PR #10에 있다. 새 작업은 그 PR 위의 별도 브랜치다.
- 운영 Spring 설정은 `ddl-auto: update`이며 Flyway 의존성과 버전 관리 SQL이 없다. 이 상태에서 두 앱 버전이 동일 MySQL에 접속하는 Blue/Green 전환을 운영 성공으로 선언할 수 없다.

## 중간 구현

- 저장소 설정의 JPA 스키마 모드, MySQL 마이그레이션 의존성, 버전 관리 SQL 존재를 검사하는 `deploy/bluegreen_readiness.py`를 추가했다.
- 보고 모드는 HOLD 이유만 출력한다. `--enforce`는 조건이 부족하면 실패 코드로 중단한다. DB 주소·비밀번호·회원 자료를 읽거나 출력하지 않는다.
- 합성 저장소 시험은 정상 조건, `update` 차단, SQL 누락 차단, 주석 처리된 의존성의 오인을 구분한다.

## 완료 근거와 한계

- 새 스크립트 1개, 시험 4개, CI 워크플로 1개, 배포 안내 1개를 변경했다. 로컬 Python 단위 시험 4개와 구문·diff 검사를 통과했다. 현재 저장소의 보고는 `BLUE_GREEN_HOLD`이며 `--enforce`는 의도대로 실패했다. 로컬 Gradle wrapper는 실행 권한이 없어 `bash ./gradlew`로 재시도했으나 배포 파일 다운로드가 네트워크에서 차단돼 Java 전체 시험·JAR은 로컬에서 실행하지 못했다. 원격 PR 게이트는 실행 결과를 따로 확인한다.
- 이 작업은 **안전하지 않은 전환을 차단하는 첫 단계**다. 마이그레이션 작성·실제 DB 검증, 두 버전 동시 기동, Nginx 전환, WebSocket 연결 유지, 롤백은 아직 구현·검증되지 않았다. Docker CLI/데몬과 운영 서버 접속 정보가 이 환경에 없어 실제 배포 성공을 주장하지 않는다.
