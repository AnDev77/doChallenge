# 로컬/CI 이미지 → ECR → EC2 digest 배포

2026-11-26. 현재 main의 API는 EC2 단일 Compose로 운영해 왔다. 기존 `compose.yaml`의 `build`는 서버의 소스와 빌드 당시 의존성으로 새 이미지를 만들었고, 커밋·배포 이미지 연결과 되돌리기를 별도로 관리하지 못했다. 이제 운영 Compose는 `API_IMAGE`만 사용한다. CI의 `compose.smoke.yaml`에만 빌드를 둔다.

## 개념 브리핑

- Git SHA: 어떤 소스로 만들었는가. OCI revision 라벨에도 기록한다.
- 이미지 tag: 사람이 읽는 이름. `git-전체SHA-빌드시각-랜덤값`으로 같은 커밋의 재빌드도 구분한다. ECR `IMMUTABLE`이 덮어쓰기를 거부한다.
- 이미지 digest: 레지스트리에 저장된 이미지 내용 식별자. 서버는 `repo@sha256:...`를 받아 실행한다. 태그가 바뀌거나 소스가 같아도 다른 이미지가 실행되는 일을 막는다.
- `release.json`: SHA·digest·플랫폼을 담는 비밀값 없는 배포 기록. 로컬 스모크를 통과한 이미지를 다시 빌드하지 않고 push한다.

현재 AWS/EC2와 ECR 후보 파이프라인을 재사용하므로 ECR을 선택했다. GHCR도 가능하지만 새 인증/레지스트리를 도입할 이유가 적다. 평소에는 CI가 검증한 이미지의 `release-manifest` 아티팩트를 배포하는 경로를 권장한다. 로컬 즉시 배포도 같은 manifest와 apply 코드를 사용한다. 공식 Docker 문서의 build-once 흐름, AWS의 immutable tags·digest 배포 기능을 적용한 선택이며 모든 기업의 보편적 표준이라고 주장하지 않는다.

## 최초 1회: 준비

Windows에서는 **WSL2 Ubuntu + Docker Desktop의 WSL 통합**, Python 3, Git, AWS CLI v2, Docker Buildx, OpenSSH를 사용한다. 로컬 publish는 병합된 최신 main의 깨끗한 checkout에서만 실행되므로 학습용 결함 브랜치가 배포되지 않는다.

1. [기존 ECR 후보 스택](candidate/README.md)을 준비한다. ECR `shockgraph-java-candidate`는 `IMMUTABLE`, EC2 인스턴스 역할에는 이 저장소 pull 권한을 준다. 이미 생성돼 있으면 다시 생성하지 않는다. 로컬은 AWS SSO/기존 승인 프로필을 사용한다. AWS 키를 Git이나 Docker 빌드 인자에 넣지 않는다.
2. 로컬 프로필에는 대상 ECR의 `DescribeRepositories`, `DescribeImages`, `BatchCheckLayerAvailability`, `InitiateLayerUpload`, `UploadLayerPart`, `CompleteLayerUpload`, `PutImage`, `BatchGetImage`, `GetDownloadUrlForLayer`, 그리고 `GetAuthorizationToken` 권한이 필요하다. 저장소 작업 권한은 해당 ECR ARN에 한정하고 `GetAuthorizationToken`만 리전 조건과 `Resource: *`를 사용한다. CI OIDC 전용 역할의 신뢰 정책을 로컬 로그인용으로 바꾸지 않는다.
3. 로컬 `~/.ssh/config`에 `Host shockgraph-prod`, `HostName <본인 EC2 주소>`, `User ubuntu`, 본인 키의 `IdentityFile`을 설정한다. `ssh shockgraph-prod`로 호스트 키와 접속을 처음 한 번 확인한다. 스크립트는 호스트 키 검증을 끄지 않는다.
4. EC2에서 검토·병합한 main의 배포 스크립트와 Compose를 한 번 갱신한다. 기존 운영은 `~/doChallenge`, Compose 프로젝트 `deploy`, 서비스 `api/mysql/redis/nginx`여야 한다. API와 Nginx가 실행 중이어야 하며 이 스크립트는 최초 DB 설치나 Blue/Green 스택 전환을 수행하지 않는다. AWS CLI/Docker Compose(`up --wait`)와 `sudo -n` 실행이 필요하다. 서버는 EC2 인스턴스 역할로 ECR에 로그인한다.
5. 새 Compose만 가져온 직후 기존 `.env`에 API_IMAGE가 없으면 일반 Compose 명령은 멈춘다. 기존 컨테이너는 계속 동작하며 아래 `deploy`는 manifest의 이미지 값을 주입해 첫 전환을 수행한다. 성공 후 `.env`에 digest를 저장한다. 먼저 수동 운영 명령이 필요하면 현재 컨테이너의 `docker inspect deploy-api-1 --format '{{.Config.Image}}'` 결과를 API_IMAGE에 넣은 뒤 첫 digest 배포를 한다.

## 반복 실행: 내 PC에서 두 명령

```bash
# 승인된 AWS 프로필로 인증. SSO 프로필을 쓰는 경우:
export AWS_PROFILE=shockgraph
aws sso login --profile shockgraph

git switch main
git pull --ff-only
python3 deploy/image_release.py publish --output release.json
python3 deploy/image_release.py deploy --host shockgraph-prod --release release.json
```

`publish`는 최신 원격 main과 HEAD·작업 트리를 확인하고, `git archive`의 커밋된 파일만 빌드 입력으로 보낸다. 로컬 `.env`·미추적 키 파일은 포함되지 않는다. `linux/amd64`를 명시해 ARM 노트북에서 만든 ARM 이미지가 EC2 x86에 올라가는 일을 막는다. 동일 이미지를 별도 이름/볼륨의 MySQL·Redis와 기동해 health 및 QQQ/SPY 조회를 검사한 뒤 push한다. 시험 볼륨만 정리하고 운영 볼륨에는 접근하지 않는다. 전체 Java/Redis 회귀는 병합 PR의 CI 게이트에서 확인한다.

로컬 전체 시험이 필요하면 별도 테스트 Redis와 `REDIS_TEST_HOST`를 설정하고 `bash ./gradlew test bootJar --no-daemon`을 수행한다. 로컬 이미지 스모크가 전체 Java 시험·실제 SMTP 시험을 대신하지 않는다.

`deploy`는 SSH 설정을 이용해 release JSON만 서버 스크립트의 표준입력으로 전달한다. 매번 서버 CMD에 명령을 입력하지 않는다. 서버는 현재 healthy API의 이미지 ID를 로컬 복구 태그로 보관하고, ECR digest pull → SHA 라벨/플랫폼 검사 → API만 `up --no-build --no-deps --pull never --wait` → 실행 이미지 ID 검사 → Nginx 설정 검사/reload → `.env`의 API_IMAGE 저장을 수행한다. Nginx reload는 API 컨테이너 교체로 달라진 IP를 다시 해석하기 위해 필요하다.

단일 컨테이너를 교체하므로 짧은 중단이 있을 수 있다. 건강 검사 실패 시 이전 이미지로 자동 복귀하지만 **이미 적용된 Flyway DB 변경은 복구하지 않는다**. DB 백업·기존 버전과 호환되는 추가 스키마를 전제로 배포한다. 무중단 전환과 전체 스키마 준비 게이트는 별도 Blue/Green 경로를 따른다. 성공 출력은 내부 health와 Nginx reload 확인이며, 실제 공개 HTTPS·로그인·브리핑은 별도 외부 인수로 확인한다.

## 버전 기록과 이전 버전으로 복귀

로컬 `release.json`은 무시 파일이다. 릴리스마다 별도 파일로 보관한다 (`--output release-<날짜>.json`). CI `EC2 ECR candidate`의 수동 main 실행도 검증했던 이미지의 동일 형식 `release-manifest`를 아티팩트로 남긴다. 그 파일을 다운로드해 로컬 `deploy --release ...`에 그대로 넣을 수 있다. EC2의 `deploy/.image-release-history.jsonl`에는 시도 시각·SHA·digest·이전 이미지 ID·복구 태그와 건강 확인 이력을 남긴다. 비밀값은 넣지 않는다.

```bash
python3 deploy/image_release.py deploy --host shockgraph-prod --release release-previous.json
```

이전 digest가 ECR에 남아 있어야 한다. 사용하는/복귀할 digest를 ECR 수명주기 정책으로 지우지 않는다. 실패 직후 자동 복구가 성공하면 `.env`는 보관한 로컬 복구 태그를 가리키므로 일반 Compose와 CAMT run-once도 그 이미지를 사용한다. 서버를 재생성할 때는 ECR의 이전 manifest로 복구한다. DB 비호환 변경 후에는 이미지 교체만으로 복구했다고 판단하지 않는다.

## 지금 검증한 범위

Python 계약 시험은 입력 거부, 운영 DB를 재시작하지 않는 명령, digest/라벨 검증, `.env` 보존, 실패 시 복구를 모의한다. 실제 ECR push와 EC2 교체는 AWS/SSH 인증 및 최초 서버 스크립트 반영 후 실행해야 한다. 기존 학습용 PR #40과 이 제품 변경은 별개다.

학습 질문: 같은 Git SHA로 두 번 빌드하면 digest도 항상 같을까? mutable tag가 교체되면 기존 컨테이너는 즉시 바뀔까? 앱 이미지 복구가 Flyway 스키마까지 되돌리지 못하는 이유는 무엇일까?

공식 참고: https://docs.aws.amazon.com/AmazonECR/latest/userguide/docker-push-ecr-image.html · https://docs.aws.amazon.com/AmazonECR/latest/userguide/image-tag-mutability.html · https://docs.docker.com/reference/cli/docker/compose/up/
