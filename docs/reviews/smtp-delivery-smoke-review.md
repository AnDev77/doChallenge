# 지정 수신처 SMTP 연결 시험

기준일: 2026-11-02. 대상: doChallenge 초안 PR #7.

## 시작 근거와 목표

사용자가 프로젝트 메일 계정과 앱 비밀번호 준비 및 Actions Secrets 등록을 알렸다. 저장된 비밀값을 읽거나 유효하다고 추정하지 않는다. 시작 시 main은 `8dd842c`, PR #7은 `c2b7dba`였고 main에는 Actions 워크플로가 없었다. 기존 작업 트리의 변경을 보존하고 별도 worktree에서 이어서 작업했다. 오늘의 범위는 지정한 본인 수신처에만 수동으로 한 번 보내는 실행 경로와 그 경로의 안전성 검사다.

## 중간 구현과 작업량

- 기존 워크플로에 기본값 false인 `send_test_mail` 입력과 전용 job을 추가했다. PR 검사는 SMTP job을 생략하며, 전체 시험 성공 후 수동 실행에서만 발송한다.
- Gradle `smtpDeliverySmoke` 작업은 일반 test/check/build에 연결하지 않는다. Java 21 시험용 실행기가 실제 `SmtpEmailVerificationSender`를 사용하며 운영 JAR에는 포함되지 않는다.
- Gmail 587, 명시적 opt-in, 필수 설정 6개, 한 개 수신 주소, 발신/인증 계정 일치를 검증한다. TLS·서버 인증서 이름 검사·5초 제한을 사용하고 메일 debug는 끈다.
- 실제 가입 코드나 Redis 상태를 생성하지 않는다. 시험 문구는 가입에 사용할 수 없다. 한 실행에서 한 번만 발송하며 오류 후 자동 재시도하지 않는다.
- 7개 새 시험 메서드로 누락 설정, 수신처 목록/헤더 주입, 잘못된 전송 설정, 단일 호출, 오류 정보 비공개, TLS 설정, 실제 발송 클래스의 단일 수신처/CC·BCC 부재를 확인한다. 기존 JAR 검사에 시험 실행기 제외 검사를 추가했다.
- 발신 주소는 생성자에서 주입한다. 검토 중 시험용 reflection 유틸리티가 DEBUG 설정에서 주소를 기록할 가능성을 발견하여 그 사용을 제거했다.

## 상태 해석

| 출력 | 뜻 | 후속 확인 |
|---|---|---|
| CONFIGURATION_REJECTED | 필수 설정·주소·명시적 실행 조건 중 하나가 맞지 않아 전송 전 중단 | Secrets의 이름/공백/발신 주소 일치를 직접 확인 |
| SMTP_AUTHENTICATION_FAILED | SMTP 계정 인증 실패 | 앱 비밀번호와 계정 정책 확인 |
| SMTP_FAILED_DELIVERY_UNKNOWN | 연결/전송 오류, 수신 여부 단정 불가 | 받은편지함·스팸함 먼저 확인 후 재실행 판단 |
| SMTP_ACCEPTED_INBOX_UNCONFIRMED | SMTP 서버가 전송 요청을 수락 | 본인 받은편지함에서 실제 수신 확인 필요 |

메일 주소·비밀번호·SMTP 예외 메시지/스택은 출력하지 않는다. 같은 job을 사람이 다시 실행하면 새 메일이 발송될 수 있으므로 수신 상태 확인 없이 반복하지 않는다. 동시에 실행하지 못하도록 job concurrency를 적용했지만 여러 수동 실행 간 중복 발송을 영구 차단하는 장치는 아니다.

## 검증·오류·한계

로컬에는 Java 17만 있고 프로젝트는 Java 21을 요구한다. Gradle 8.5 다운로드도 `Network is unreachable`로 실패하여 로컬 Java 시험/빌드 성공을 주장하지 않는다. 전체 Java 시험·실제 Redis 시험·실행 JAR 검사는 원격 CI에서 확인하고 해당 실행 링크를 PR에 기록한다. 단위 시험은 실제 외부 메일을 보내지 않는다.

현재 GitHub 연결 도구는 workflow_dispatch를 제공하지 않아 이 작업에서 실제 발송 실행을 완료할 수 없다. Secrets의 존재나 유효성, SMTP 접수, 실제 받은편지함 도착은 아직 미확인이다. main에 워크플로가 없으므로 GitHub 화면에 Run workflow 버튼이 없을 수 있다. GitHub 공식 문서상 이미 실행된 워크플로는 CLI/API에서 브랜치를 지정해 수동 실행할 수 있다. 권한 있는 로컬 GitHub CLI에서 다음을 사용한다.

```sh
gh workflow run email-signup-quality.yml --repo AnDev77/doChallenge --ref feat/email-signup-proof -f send_test_mail=true
```

실행 후 `smtp-smoke` job의 결과와 본인 메일함을 각각 확인한다. 명령이 404/422 등으로 거부되면 오류에 비밀값 없이 상태만 기록하고 중단한다. 이 시험을 위해 PR을 자동 병합하지 않는다. GitHub 공식 안내: https://github.com/github/docs/blob/main/content/actions/reference/workflows-and-actions/events-that-trigger-workflows.md#workflow_dispatch

## 객관적 종료 판정 기준

설정 검증과 실패 시 비공개 처리, 전용 실행 경로가 이번 증가분이다. 실제 수신, 회원가입 전체 흐름, 운영 DB/Redis/CORS 연결, 공개 회원 서비스 배포는 별도 인수 조건이다. SMTP 서버 접수만으로 수신 완료나 이메일 인증 기능 배포 완료를 주장하지 않는다. 새로운 SMTP 시험 도구가 연구 모델의 예측 성능이나 제품 수요를 입증하지도 않는다.

## 완료 근거

코드 `62725f0`의 [원격 Java 21 검사](https://github.com/AnDev77/doChallenge/actions/runs/41366725135)에서 전체 54개 시험이 실패 0·오류 0·생략 0으로 완료됐다. 기존 47개에 SMTP 안전성/메시지 구성 7개가 추가됐다. 실제 Redis 통합 시험과 bootJar 빌드, 가입 응답 클래스 포함 및 SMTP 시험 실행기 제외 검사도 성공했다. `smtp-smoke` job은 pull_request 실행이므로 의도대로 skipped였으며 실메일 검증 성공으로 계산하지 않는다. 로컬 Java 시험은 환경 제약으로 미실행, 실제 SMTP·수신함·운영 배포는 미확인 상태다. 최종 문서 반영 커밋의 원격 게이트는 PR 본문에서 별도로 추적한다.
