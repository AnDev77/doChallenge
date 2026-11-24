# CAMT SEC 운영 수집 자동화

2026-11-24 기준. 검토한 GitHub `main`에서 **Actions → CAMT SEC production ingest → Run workflow** 한 번으로 운영 EC2의 기존 Java 이미지가 SEC CAMT 30일 공시를 수집한다. 별도의 비웹 컨테이너로 실행하며 원문 파일은 `/home/ubuntu/shockgraph-sec-filings`에, 문서·청크·검색 준비 상태는 기존 운영 MySQL에 저장한다. 완료 후 GitHub Actions 로그에는 수집/준비 건수와 `KEYWORD_READY` 문서의 SEC URL·접수/수집 시각·원문 해시·본문 최대 1,000자만 표시한다. 모델 호출·초안 승인·API 제공자 변경·프론트 배포는 하지 않는다.

## 처음 한 번만 설정

1. EC2의 `/home/ubuntu/doChallenge/deploy/.env`에 식별 가능한 연락처를 `SEC_USER_AGENT=ShockGraph Operator operator@example.com` 형태로 넣는다. 실제 운영 연락처로 바꾸고 `chmod 600 deploy/.env`를 유지한다. 값을 GitHub PR이나 채팅에 적지 않는다. EC2에 Docker Compose의 운영 `api` 이미지, MySQL, Redis가 실행 중이어야 한다. 이 설정이 없으면 작업은 `config` 단계에서 종료된다.
2. 이미 구성한 GitHub OIDC 공급자와 SSM 온라인 인스턴스를 사용한다. 검토한 `main`의 파일로 AWS CloudShell 등 관리 환경에서 다음 템플릿을 만든다. `i-REPLACE`는 운영 EC2 ID로 바꾸고, 생성되는 IAM 역할이 **이 EC2와 `ShockGraph-IngestCamtV1` 문서에만** `SendCommand`를 허용하는지 검토한다. 기존 읽기 전용 점검 역할이나 격리 후보 역할을 수정하지 않는다.

   ```bash
   python3 -B deploy/camt_ingest/render_stack.py > /tmp/shockgraph-camt-ingest-stack.json
   aws cloudformation deploy --region us-east-2 \
     --stack-name shockgraph-camt-ingest \
     --template-file /tmp/shockgraph-camt-ingest-stack.json \
     --parameter-overrides InstanceId=i-REPLACE \
     --capabilities CAPABILITY_IAM
   aws cloudformation describe-stacks --region us-east-2 \
     --stack-name shockgraph-camt-ingest \
     --query 'Stacks[0].Outputs[*].[OutputKey,OutputValue]' --output table
   aws ssm describe-document --region us-east-2 --name ShockGraph-IngestCamtV1 \
     --document-version 1 --query 'Document.[Status,HashType,Hash]' --output text
   ```

3. GitHub **Settings → Environments**에 `ec2-camt-ingest`를 만들고 **Selected branches and tags → Branch → main**만 허용한다. 환경 승인자를 설정할 수 있으면 운영 수집 담당자를 지정한다. 환경 변수 `AWS_CAMT_INGEST_ROLE_ARN`에는 `RunRoleArn`, `EC2_INSTANCE_ID`에는 대상 ID, `AWS_CAMT_DOCUMENT_SHA256`에는 위 SSM 문서 버전 1의 SHA-256을 넣는다. AWS 장기 액세스 키나 DB 비밀번호는 GitHub에 넣지 않는다. 이 환경은 `workflow_dispatch`의 `main` 실행만 접근한다.

## 반복 실행

Actions에서 `CAMT SEC production ingest`를 선택하고 `main`으로 실행한다. 수집 명령에는 사용자가 바꿀 수 있는 symbol, SQL, 쉘, 모델 키 파라미터가 없다. 중복 원문은 기존 `(symbol, accession, filename, raw_sha256)` 검사로 다시 저장하지 않는다. 동시 실행은 GitHub concurrency와 EC2 파일 잠금으로 차단한다. `new_versions=0`도 기존 30일 문서가 준비되어 본문을 반환하면 정상이다.

실행 결과의 `accession`, `accepted_at`, `fetched_at`, `index_status`, `source_url`, `excerpt`를 SEC 원문과 대조한다. `CAMT_INGEST_FAILED:config`면 서버 연락처/파일 권한, `ingest`면 EC2의 SEC 네트워크·Java 이미지/DB·무결성, `evidence`면 준비된 30일 근거·DB를 운영자가 확인한다. 실패 로그에는 비밀값이나 임의 Docker/SQL stderr가 나오지 않는다. SSM 명령의 시간 초과 시 서버 작업 종료를 확인한 뒤 다시 실행한다.

이 결과는 실제 수집과 **원문 청크 본문 확인**의 증거다. `BRIEFING_PUBLIC_PROVIDER=java`와 명시적 `?window=30d` 생성·승인 경로는 별도 PR #37·#38 및 사람 검토가 필요하다. 해당 PR이 병합돼도 수집 결과만으로 공개 브리핑을 `ready`로 바꾸지 않는다. 첫 실제 운영 실행 후에만 정기 스케줄을 추가한다. SSM 문서 명령을 바꾸려면 새 버전·해시·IAM 범위를 다시 검토한다.
