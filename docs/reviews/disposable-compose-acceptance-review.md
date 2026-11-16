# 격리 Docker 배포 구성 인수 시험

## 시작 근거 (2026-11-06)

- 원격 main과 `feat/first-deploy-stack`의 최신 커밋, 작업 트리, 웹의 일일 계획을 확인했다. 배포 구성은 Java·MySQL·Redis·Nginx와 운영 SMTP/TLS를 요구하지만, 이 개발 환경에는 Docker CLI와 데몬이 없다. Gradle wrapper도 배포 파일 다운로드가 네트워크에서 차단돼 로컬 전체 시험을 실행하지 못했다.
- 기존 단위/통합 시험은 각 기능을 검증한다. 하나의 Compose 스택을 거쳐 이메일 인증부터 종목방 재접속까지 이어지는 실제 HTTP/STOMP 인수 시험은 없었다. 현재 브랜치 위에 별도 작업 트리를 만들었다.

## 중간 구현과 검사

- 시험 전용 Compose 오버레이에 Mailpit을 추가했다. Java는 `prod,smoke` 프로필로 실제 MySQL·Redis에 연결하며 SMTP 인증/TLS만 격리 SMTP에 맞게 끈다. Nginx에는 일회용 자체 서명 인증서를 제공한다.
- 공통 `deploy/prepare_smoke.py`가 로컬·CI에서 같은 임시 비밀번호와 자체 서명 localhost 인증서를 만든다. 스모크 스크립트는 Nginx의 HTTPS 경로에서 메일 발송·수신, 코드 확인, 일회용 증표로 가입, 로그인, 미인증 쓰기 거부, 인증된 WebSocket/STOMP 구독과 실시간 수신, 중복 요청, 연결 종료 후 `afterId` 복구, QQQ/SPY 분리를 검사한다. 합성 메일 주소와 내용만 사용하며 코드·증표·JWT 본문은 출력하지 않는다.
- `python3 -m py_compile`, YAML 파싱, `git diff --check`는 구문·형식만 확인한다. 첫 네 원격 Actions 실행에서 Compose 계약과 5개 컨테이너 기동은 성공했다. 스모크는 메일 확인 단계에서 반복 실패했다. 첫 시도에는 Mailpit 수신자 필드 이름이 틀렸고, 다음 시도에는 본문 추출을 잘못했다. 안전한 길이·문자 종류 진단으로 메일 수신과 숫자 존재를 확인했고, 한글 바로 뒤에 붙은 6자리 숫자는 Unicode `\\b` 단어 경계에 맞지 않는다는 시험 코드 오류를 찾았다. 숫자 앞뒤의 숫자 여부만 검사하도록 수정한다. 이 실패를 전체 흐름 성공으로 기록하지 않는다.

## 완료 근거와 작업량

- 시험 오버레이 1개, 설정 생성기 1개, 스모크 실행기 1개, CI 워크플로 1개, 시험용 메일 설정 1개, 비밀 파일 제외 규칙/배포 문서/이 리뷰를 작성·수정했다. [공통 설정 생성기를 포함한 Docker 인수 시험 42155523005](https://github.com/AnDev77/doChallenge/actions/runs/42155523005)은 Compose 설정·컨테이너 기동·이메일/가입/채팅/재접속·자원 삭제 단계가 모두 성공했다. 같은 커밋의 [이메일](https://github.com/AnDev77/doChallenge/actions/runs/42155522941), [채팅](https://github.com/AnDev77/doChallenge/actions/runs/42155523024), [운영 이미지](https://github.com/AnDev77/doChallenge/actions/runs/42155523058) 품질 게이트도 성공했다.

## 판정의 경계

- 통과하더라도 합성 SMTP와 자체 서명 TLS를 사용한 **격리 환경의 Java·MySQL·Redis·Nginx 연결**만 입증한다. 사용자 지정 실제 메일 수신처, 운영 API 도메인과 인증서, Vercel 두 브라우저, DB 영속성/백업·복구, Blue/Green 무중단은 별도 검증이다.
- ShockGraph AI의 위험 예측 연구·비공개 가격은 이번 배포 구성에 넣지 않는다. 예측 성능 및 제품 수요는 HOLD다.

## Docker 재생성 뒤 회원·대화 원본 확인 (2026-11-06)

- 시작: 기존 인수 시험은 합성 회원과 대화를 만든 뒤 곧바로 볼륨을 삭제했다. `mysql_data`가 서비스 컨테이너 재생성 뒤에도 남는지는 확인하지 않았다. 현재 PR #10의 원격 head를 확인한 뒤 같은 브랜치에서 작업했다.
- 중간: `deploy/smoke_test.py`의 기존 이메일 인증·가입·QQQ/SPY 채팅 검사가 끝나면, 시험 Compose 스택을 `down`(볼륨 삭제 없음) 후 `up --wait`로 재생성한다. 같은 회원의 로그인과 `afterId`로 대화 원본 한 건을 다시 읽는다. 이름 있는 볼륨을 유지하면서 API·Nginx·Redis도 새로 띄우는 경로다.
- 완료: 코드 1개와 배포 안내 1개를 수정했다. Python 구문 및 diff 형식 검사를 통과했다. 실제 Docker 재생성·회원·대화 검사는 원격 인수 시험의 최종 상태를 확인해 판정한다. 이 실행 환경에는 Docker CLI/데몬이 없어 로컬 기동 결과로 주장하지 않는다.
- 범위: 동일 호스트의 Compose 볼륨 보존만 검사한다. 디스크 손상, 다른 호스트로의 이전, MySQL 덤프 복원, 운영 SMTP/TLS, Blue/Green 전환 성공은 입증하지 않는다. 비공개 가격·모델 예측·제품 수요는 이 시험과 무관하며 HOLD다.
