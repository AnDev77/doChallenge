# 첫 공개 배포: Java 회원·QQQ/SPY 채팅

2026-11-02 기준. 웹은 기존 Vercel 주소를 유지한다. 이 스택은 **Java + MySQL + Redis + Nginx**만 실행한다. ShockGraph AI의 PostgreSQL은 연구 저장소이며 이 회원 DB와 섞지 않는다. Python의 미검증 예측이나 비공개 R2 가격은 이 배포에 포함하지 않는다.

## 배포 전 격리 인수 시험

`.github/workflows/deploy-smoke.yml`은 GitHub Actions의 일회용 Docker 환경에서 `compose.yaml`과 시험 전용 `compose.smoke.yaml`을 함께 실행한다. 무작위 시험 비밀번호와 1일짜리 자체 서명 인증서를 생성하고, 외부 수신자 대신 내부 Mailpit SMTP를 사용한다. `deploy/smoke_test.py`는 Nginx HTTPS를 통해 인증 메일 수신→코드 확인→일회용 증표 가입→로그인→미인증 쓰기 거부→QQQ/STOMP 실시간 수신→중복 요청 방지→연결 종료 후 `afterId` 복구→SPY 분리를 확인한다. 이어서 이름 있는 MySQL 볼륨은 보존한 채 컨테이너를 재생성하고 기존 회원의 재로그인·대화 기록 재조회를 확인한다. 코드·증표·JWT·메일 본문은 시험 로그에 출력하지 않는다.

이 시험의 성공은 **격리된 합성 회원·메일·메시지의 서비스 연결과 동일 Docker 호스트의 볼륨 유지**에 한정된다. 실제 SMTP 수신함, Vercel 화면의 두 브라우저, 운영 도메인/TLS, 서버 디스크 손상 뒤의 백업 복구는 별도 인수 시험이 필요하다. 로컬 Docker가 설치된 개발자는 다음과 같이 재현할 수 있다. 80/443 포트가 비어 있어야 한다. `down -v`는 **이 격리 시험의 MySQL 볼륨을 삭제**하므로 실제 운영 데이터에 사용하지 않는다. 시험 오버레이와 자체 서명 인증서는 운영 환경에 사용하지 않는다.

```bash
python3 deploy/prepare_smoke.py
docker compose --env-file deploy/.smoke.env -f deploy/compose.yaml -f deploy/compose.smoke.yaml config --quiet
docker compose --env-file deploy/.smoke.env -f deploy/compose.yaml -f deploy/compose.smoke.yaml up -d --build --wait --wait-timeout 360
python3 deploy/smoke_test.py
docker compose --env-file deploy/.smoke.env -f deploy/compose.yaml -f deploy/compose.smoke.yaml down -v --remove-orphans
```

## 데이터와 외부 경계

| 구성 | 역할 | 지속성/접근 |
|---|---|---|
| MySQL 8.4 | 회원·금융 대화 원본 | `mysql_data` 영속 볼륨, 외부 포트 없음, 배포 전 백업 대상 |
| Redis 7.4 | 인증 코드·일회용 증표·제한 및 기존 채팅 캐시 | 재시작 시 소멸 가능, 외부 포트 없음. 재시작 뒤 진행 중 이메일 인증은 다시 시작해야 함 |
| Java 21 | 인증·QQQ/SPY 메시지 API·STOMP | 내부 네트워크만; 실 DB와 Redis 연결 시에만 health UP |
| Nginx | HTTPS, `/api/`·`/ws` 프록시와 메일 발송 제한 | 공개 443, 80은 HTTPS 이동만 제공. 인증서 파일이 필요 |

동일 서버에 서버 원본을 체크아웃하고 `deploy/.env.example`을 `deploy/.env`로 복사해 **서버에서만** 실제 값을 입력한다. 예시값을 실제 배포에 쓰지 않는다. `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `JWT_SECRET`은 서로 다른 무작위 값으로 발급하고 파일 권한을 제한한다. API 도메인의 유효한 TLS 인증서를 발급하고 `TLS_CERT_PATH`·`TLS_KEY_PATH`에 서버의 절대 경로를 적는다. SMTP는 사용자가 승인한 발신 계정과 실제 시험 수신처만 이용한다. React Vercel 빌드에는 `VITE_AUTH_ENABLED=true`와 `VITE_AUTH_API_BASE_URL=https://<승인된 API 호스트>/api/v1/auth`가 필요하다. 이 값은 브라우저 공개값이며 비밀을 넣지 않는다.

```bash
cp deploy/.env.example deploy/.env
chmod 600 deploy/.env
docker compose --env-file deploy/.env -f deploy/compose.yaml config --quiet
docker compose --env-file deploy/.env -f deploy/compose.yaml up -d --build
docker compose --env-file deploy/.env -f deploy/compose.yaml ps
curl --fail https://<승인된 API 호스트>/healthz
```

MySQL·Redis·Java 포트는 인터넷에 열지 않는다. 공개 HTTPS는 Nginx가 직접 종료하고 `X-Forwarded-For`를 실제 연결 IP로 덮어쓴다. 운영 웹의 Origin을 `WEB_ORIGIN`에 정확히 지정한다. VM·도메인/TLS·SMTP 설정이 없는 상태는 **배포 준비**이며 공개 배포 성공이 아니다. 인증서 갱신 후에는 Nginx 컨테이너를 재생성해 새 파일을 읽게 한다.

## 내일 배포 확인 순서

1. 병합 순서: doChallenge PR #6 → #8 → #7 → 이 통합/배포 PR의 충돌·전체 CI 재확인. 웹 PR #1은 동일 API 계약과 원격 CI를 확인한 뒤 반영한다. 배포 직전 main 최신 커밋을 다시 확인한다.
2. 서버의 영속 디스크와 MySQL 볼륨, 서버 비밀값, HTTPS 주소, 정확한 Origin을 확인한다. 기존 DB가 있으면 먼저 덤프를 만든다. `ddl-auto: update`는 첫 단일 인스턴스 배포에 한정하고 다음 배포 전 버전 관리 SQL 마이그레이션으로 바꾼다.
3. `/actuator/health`는 컨테이너 내부에서 DB·Redis 연결을 확인한다. 외부에서는 HTTPS의 `/api/v1/finance/rooms/QQQ/messages` 공개 읽기와 미인증 POST 거부를 검사한다. HTTP의 308 이동이나 `/healthz` 단독 응답은 회원 서비스 성공이 아니다.
4. 지정 수신함에서 메일 발송→수신→확인→가입→로그인, 로그인 회원의 QQQ·SPY 작성, 다른 브라우저 조회와 재접속 후 누락 복구를 실제로 확인한다. 실제 수신과 두 브라우저 시험 전에는 회원·채팅 기능을 운영 성공으로 표시하지 않는다.
5. 장애 시 새 앱을 내리고 이전 앱 이미지로 되돌린다. **데이터 볼륨을 지우지 않는다.** DB 구조가 이미 바뀌었으면 앱만 되돌리는 것이 안전하다고 가정하지 않고 백업·스키마 호환성을 먼저 확인한다.

공개 API 주소와 유효한 TLS 인증서가 정해져야 실제 공개가 가능하다. 컨테이너를 띄운 것과 사용자가 HTTPS에서 회원·대화를 이용하는 것은 별도 판정이다.

## Oracle Always Free VM 경로

사용자가 2026-11-02 선택한 첫 호스트 후보는 OCI Always Free의 Ampere A1이다. [Oracle의 현재 한도](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm)는 홈 리전에서 합계 2 OCPU·12GB RAM에 해당한다. 첫 단일 VM은 `VM.Standard.A1.Flex`, 2 OCPU·12GB, Ubuntu Arm64, 기본 47GB 이상 부팅 볼륨, 공인 IPv4와 SSH 키를 사용한다. Oracle의 용량 부족 오류가 나면 무료 VM을 즉시 만들 수 없으며 배포 성공을 보장하지 않는다. Docker 공식 MySQL 8.4 이미지는 Arm64도 제공한다.

1. OCI 콘솔의 **Compute → Instances → Create instance**에서 위 Always Free 대상 shape·Ubuntu 이미지를 선택한다. 공개 서브넷/공인 IP를 지정하고 SSH 개인 키를 안전하게 보관한다. 무료 자원 표시와 결제 한도를 콘솔에서 확인한다.
2. 네트워크 보안 규칙과 Ubuntu 방화벽에서 **22/TCP는 본인 IP만**, 80/443 TCP는 공개로 연다. 3306/6379/8080은 열지 않는다. Ubuntu 로그인 사용자는 `ubuntu`다.
3. Docker Engine과 Compose 플러그인은 [Docker의 Ubuntu Arm64 공식 설치 절차](https://docs.docker.com/engine/install/ubuntu/)를 따른다. 저장소를 서버에 가져오고 `.env`·TLS 인증서를 서버 안에서만 설정한다.
4. 자체 도메인이 없다면 [Let's Encrypt의 IP 주소 인증서](https://letsencrypt.org/2026/03/11/shorter-certs-certbot/)를 임시로 쓸 수 있다. Certbot 5.4 이상과 `shortlived` 프로필이 필요하며 유효기간이 약 **6일**이라 자동 갱신·Nginx 재시작 후 검증이 필수다. 인증서 발급 전까지 `VITE_AUTH_ENABLED`·`VITE_CHAT_ENABLED`는 켜지 않는다. 도메인을 확보하면 일반 도메인 인증서로 전환한다.

인스턴스 공인 IP가 생기고 80/TCP가 열렸으며 Nginx가 아직 실행되지 않는 첫 발급에서는 다음 명령을 사용한다. `PUBLIC_IP`는 사용자가 확인한 실제 IP로 지정한다. Certbot 버전과 발급 결과를 먼저 확인하고, `deploy/.env`의 인증서 경로를 `/etc/letsencrypt/live/<공인 IP>/...`로 맞춘다.

```bash
certbot --version
sudo certbot certonly --standalone --preferred-profile shortlived --ip-address "$PUBLIC_IP"
```

IP 인증서 자동 갱신은 Certbot 타이머와 갱신 성공 시 Nginx **컨테이너 재생성**을 함께 설정하고, 만료 시각을 감시해야 한다. 6일마다 수동으로 갱신하는 운영은 첫 배포 완료로 판정하지 않는다. 발급이 제한되면 TLS를 우회하지 않고 도메인 인증서 경로를 선택한다.

## Blue/Green 확장 경로

첫 배포는 단일 Java 컨테이너로 외부 연결과 데이터 보존을 검증한다. 다음 단계에서 `api-blue`와 `api-green`을 서로 다른 **버전 태그의 이미지**로 실행하고, 두 컨테이너가 같은 MySQL·Redis에 연결되게 한다. Nginx는 검증된 한 색상으로만 신규 트래픽을 보내며, 대기 색상은 DB/Redis health와 인증·읽기·쓰기 합성 스모크를 통과한 뒤 전환한다. 전환 뒤 오류가 나면 Nginx를 이전 색상으로 되돌리고 대기 인스턴스를 유지한다.

두 색상이 같은 DB를 읽는 동안 `ddl-auto: update`로 스키마를 임의 변경하면 안전한 되돌리기를 보장할 수 없다. Blue/Green 전에는 버전 관리 마이그레이션과 **구버전·신버전 동시 호환**을 먼저 구현한다. 이메일 인증 증표와 채팅 원본은 색상별로 나누지 않는다. 단일 VM Blue/Green은 VM 장애까지 막지 못하며, 실제 무중단 여부는 연결 중인 WebSocket과 진행 중 요청의 전환 시험으로 판정한다. Jenkins는 이 순서를 자동화할 수 있으나 첫 배포 필수는 아니다.

## 금융 종목방 Redis 캐시와 성능 판정 계획

첫 배포의 금융 메시지 원본은 MySQL이며 `FinanceChatService.messages`는 DB에서 최근 30개 또는 `afterId` 이후 30개를 읽는다. 기존 운동 모임의 `RedisChatRecentMessageCache`를 금융방에 그대로 연결하지 않는다. 종목별 읽기 패턴과 재접속 커서가 다르기 때문이다. Redis는 현재 인증 코드·가입 증표·제한과 기존 채팅 캐시에 이미 쓰인다.

1. **캐시 전 기준 측정:** 합성 QQQ/SPY 메시지를 로컬 시험 DB에 적재하고, 최근 30개 조회와 `afterId` 재접속 조회를 분리해 동시 사용자 수별 p50/p95 응답 시간, 초당 요청, MySQL 조회 수·CPU, Java/Redis 메모리를 측정한다. 날짜·부하 조건·코드 커밋을 같이 기록하고 실제 회원 메시지나 본문은 성능 로그에 싣지 않는다.
2. **작은 범위부터 적용:** `GET /api/v1/finance/rooms/{symbol}/messages`의 **최근 30개 조회만** `finance:room:{symbol}:recent:v1` 키로 캐시한다. `afterId`는 누락 복구의 정확성이 우선이므로 DB에서 읽는다. 캐시에는 ID·작성자 ID·본문·생성 시각을 넣되 외부 공개 Redis를 만들지 않고 TTL과 최대 길이 30개를 둔다. 캐시 미스와 Redis 장애는 DB로 돌아간다.
3. **쓰기 순서:** MySQL 트랜잭션 커밋 뒤에만 해당 종목의 최근 목록 캐시를 무효화하거나 안전하게 갱신한다. 롤백·중복 requestId·동시 쓰기에서 유령/중복 메시지가 나오지 않는 시험을 먼저 통과시킨다. 캐시는 보조 사본이며 MySQL을 최종 원본으로 유지한다.
4. **동일 조건 재측정:** 캐시 전후에 같은 합성 메시지·동시성·하드웨어로 p50/p95, DB 조회 수, 캐시 적중률, 쓰기 지연과 Redis 메모리를 비교한다. 조회 지연 또는 DB 부하가 줄어도 재접속 누락·오래된 목록·Redis 장애 시 실패가 늘면 채택하지 않는다. 측정 결과가 나오기 전에는 성능 개선을 주장하지 않는다.

이 작업은 첫 공개 배포의 DB·Redis 연결과 실제 대화 인수 시험 **이후**에 진행한다. 배포가 늦어지지 않도록 캐시 미구현을 첫 배포 차단 사유로 취급하지 않는다.
