# 첫 배포 스택과 이메일·채팅 통합 검토

기준일: 2026-11-02. 비밀값·실메일 주소·회원 메시지 본문은 기록하지 않는다.

## 시작

- main `8dd842c`에는 MySQL·Redis·Mailpit의 **로컬** Compose만 있고, Java 운영 이미지·Nginx HTTPS·운영 설정이 없었다.
- 초안 PR #6은 STOMP 인증, #8은 QQQ/SPY 메시지 저장·조회·재접속을 추가한다. 초안 PR #7은 이메일 가입의 일회용 증표와 발송 제한을 추가한다. #8 위에서 #7을 실제 병합해 파일 충돌이 없음을 확인했다. 웹 초안 PR #1은 가입·로그인 화면을 갖지만 운영 Java 연결 전에는 비활성이다.
- 사용자 결정은 내일 첫 배포, Docker/Nginx, Redis와 DB 선정이다. Java는 MySQL 드라이버/엔터티를 사용하므로 첫 배포 DB는 MySQL로 고정한다. ShockGraph AI의 PostgreSQL 연구 DB를 회원 DB로 오인하지 않는다.

## 중간 구현

- Java 21 다단계 Docker 이미지, MySQL 영속 볼륨·Redis 내부 네트워크, 운영 프로필, health 체크, Nginx HTTPS와 `/api/`·`/ws` 프록시를 추가했다. DB/Redis/API는 외부 포트를 게시하지 않는다. 비밀값은 Compose 실행 시 외부 `.env`에서 요구하며 예시값은 운영에 쓰지 않는다.
- `/actuator/health`만 인증 없이 허용하고 Nginx 공개 라우트에는 추가하지 않았다. Java 헬스는 DB/Redis 연결을 확인한다. Nginx 메일 발송 요청 제한과 기존 Java 제한을 함께 사용한다.
- 금융 메시지 Redis 캐시 계획을 `deploy/README.md`에 기록했다. 첫 배포는 MySQL 원본 읽기로 정확성을 먼저 검증하고, 합성 부하에서 캐시 전후 p50/p95·DB 조회량·캐시 적중률을 비교한 뒤 채택한다. `afterId` 복구는 DB에 남기고 Redis 실패 시 DB로 돌아간다.

## 완료 기준과 현재 한계

- 작업량: 기존 두 기능 분기의 충돌 없는 통합 1건, 운영 파일 8개 이상, 보안/헬스 설정 2곳, 배포 게이트 1개, 실행·복구 문서 1개.
- 로컬 Docker가 없어 실제 컨테이너 기동은 실행하지 못한다. GitHub Actions에서 Java 21 전체 시험·Redis 통합 시험·실행 JAR·Compose 설정·Docker 이미지·Nginx 설정을 각각 검사한다. 게이트 결과는 PR에 실행 링크로 기록한다.
- 첫 원격 실행에서 배포 스택 게이트와 이메일 게이트는 성공했으나 기존 채팅 게이트는 Redis 서비스가 없어 이메일 통합 시험이 생략된 사실을 실패로 판정했다. 채팅 게이트에도 Redis 서비스를 추가해 생략 없는 시험으로 다시 확인한다. 웹 Vercel에서 금융 REST를 읽고 쓰기 위해 금융 방 경로의 허용 Origin과 Security CORS 처리를 명시했다.
- VM, 공개 API 주소와 TLS 인증서, SMTP 발신 설정과 지정 수신함, Vercel 운영 빌드 설정은 외부 입력이다. 이 값 없이 실제 HTTPS 회원 가입과 두 브라우저 채팅을 성공이라고 주장할 수 없다.
- 사용자는 Oracle Always Free VM을 선택했으나 아직 인스턴스가 없다. 홈 리전 Ampere A1의 무료 할당·용량 부족 위험, Ubuntu Arm64·공인 IP·TLS 발급 경로를 `deploy/README.md`에 추가했다. 자체 도메인 없이 공개 IP 인증서를 택하면 약 6일 만료와 자동 갱신 검증이 운영 조건이다.
- Blue/Green은 별도 Java 두 이미지와 Nginx 전환, 공유 MySQL/Redis, 구·신 DB 스키마 호환, 전환·되돌리기 시험으로 설계했다. 현재 Compose는 **단일 슬롯**이며 Blue/Green 또는 무중단을 구현·검증했다고 주장하지 않는다.
- 첫 배포에는 MySQL `ddl-auto: update`가 남는다. 첫 데이터가 생긴 후에는 버전 관리 DB 마이그레이션과 백업/복원 시험이 필요하다. 채팅 신고·삭제와 Redis 금융 캐시, 실제 수요·금융 예측 성능은 별도 HOLD다.
