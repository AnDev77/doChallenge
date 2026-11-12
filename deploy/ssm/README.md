# GitHub Actions에서 EC2 상태 확인

GitHub Actions의 **EC2 SSM read-only preflight**는 AWS Systems Manager를 통해 지정한 EC2 한 대에 연결합니다. Docker 데몬, Docker Compose, `/home/ubuntu/doChallenge/.git`, `deploy/.env`의 상태만 반환합니다. `.env`의 내용, 인증서 키, AWS 임시 자격 증명은 읽거나 출력하지 않습니다. 이 시험이 성공해도 Java·MySQL·Redis·SMTP·채팅의 배포 성공은 아닙니다.

## 최초 한 번: AWS 계정 소유자

1. EC2가 같은 리전의 **Systems Manager → Fleet Manager → Managed nodes**에서 온라인인지 확인합니다. EC2에 붙인 인스턴스 역할은 그대로 사용합니다.
2. 검토한 [SSM 문서 JSON](read-only-preflight.json)을 내려받아 AWS CloudShell에 `read-only-preflight.json` 이름으로 올립니다. 다음 명령에서 리전이 다른 경우 실제 리전으로 바꿉니다.

   ```bash
   aws ssm create-document --region us-east-2 \
     --name ShockGraph-ReadOnlyPreflight --document-type Command \
     --document-format JSON --target-type /AWS::EC2::Instance \
     --content file://read-only-preflight.json

   aws ssm describe-document --region us-east-2 \
     --name ShockGraph-ReadOnlyPreflight --document-version 1 \
     --query 'Document.[Status,HashType,Hash]' --output text
   ```

   `Active`, `Sha256`, 해시를 확인합니다. 이미 같은 이름의 문서가 있으면 내용을 확인하고, 이름이 같다는 이유만으로 기존 문서를 바꾸거나 해시를 등록하지 않습니다. 이 워크플로는 검토한 **버전 1과 AWS가 계산한 SHA-256**을 고정합니다.

3. IAM에 GitHub OIDC 공급자 `https://token.actions.githubusercontent.com`(대상 `sts.amazonaws.com`)를 확인하고, **GitHub용 별도 역할**을 만듭니다. 아래 두 JSON의 `ACCOUNT_ID`, `REGION`, `INSTANCE_ID`를 실제 값으로 바꿉니다. 기존 EC2 역할과 섞지 않습니다.

   **역할의 신뢰 정책:**

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [{
       "Effect": "Allow",
       "Principal": {
         "Federated": "arn:aws:iam::ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com"
       },
       "Action": "sts:AssumeRoleWithWebIdentity",
       "Condition": {
         "StringEquals": {
           "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
           "token.actions.githubusercontent.com:sub": "repo:AnDev77/doChallenge:environment:ec2-probe"
         }
       }
     }]
   }
   ```

   위 subject는 GitHub 기본 형식입니다. 저장소가 숫자 ID가 포함된 immutable subject를 사용한다면, 저장소의 실제 형식으로 바꾸고 정확한 일치를 유지합니다. [GitHub OIDC subject 공식 안내](https://docs.github.com/en/actions/reference/security/oidc#example-subject-claims)를 참고하세요.

   **역할의 권한 정책:**

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Effect": "Allow",
         "Action": "ssm:SendCommand",
         "Resource": [
           "arn:aws:ssm:REGION:ACCOUNT_ID:document/ShockGraph-ReadOnlyPreflight",
           "arn:aws:ec2:REGION:ACCOUNT_ID:instance/INSTANCE_ID"
         ]
       },
       {
         "Effect": "Allow",
         "Action": "ssm:GetCommandInvocation",
         "Resource": "*",
         "Condition": {"StringEquals": {"aws:RequestedRegion": "REGION"}}
       }
     ]
   }
   ```

   `GetCommandInvocation`은 [AWS 권한표](https://docs.aws.amazon.com/service-authorization/latest/reference/list_ssm.html#list_awssystemsmanager-actions-as-permissions)상 리소스 ARN 제한을 지원하지 않아 리전으로 제한합니다. 이 읽기 권한으로 같은 리전의 다른 실행 ID를 알고 있다면 해당 출력도 조회할 수 있으므로, 관리 작업의 SSM 출력에도 비밀값을 남기지 않습니다. 역할에는 `AWS-RunShellScript`, 문서 수정, 셸 세션, 배포 권한을 추가하지 않습니다.

## 최초 한 번: GitHub 저장소

1. **Settings → Environments → New environment**에서 `ec2-probe`를 만듭니다. **Deployment branches and tags → Selected branches and tags**에서 브랜치 `main`만 허용합니다. 배포 환경의 브랜치 제한을 적용할 수 없다면 OIDC 역할을 활성화하지 않습니다.
2. 이 환경의 **Environment variables**에 다음 네 값을 넣습니다. 장기 AWS 액세스 키나 SMTP/JWT 값을 이 시험에 넣지 않습니다.

   | 변수 | 값 |
   |---|---|
   | `AWS_REGION` | 예: `us-east-2` |
   | `EC2_INSTANCE_ID` | 대상 EC2의 `i-...` |
   | `AWS_SSM_PROBE_ROLE_ARN` | 위에서 만든 GitHub용 역할 ARN |
   | `AWS_SSM_PROBE_DOCUMENT_SHA256` | AWS가 출력한 문서 버전 1의 SHA-256 |

3. 승인 후 워크플로가 `main`에 들어가면 **Actions → EC2 SSM read-only preflight → Run workflow → main**을 실행합니다. PR의 시험은 AWS 권한을 받지 않습니다.

## 결과 해석

- `EC2 SSM connection verified; deployment was not attempted.`: 고정 문서가 지정 EC2에서 성공했고 허용된 상태 항목만 돌아왔습니다. `deploy_env=missing`이거나 Docker가 `unavailable`이면 서버 준비는 별도로 보완해야 합니다.
- AWS 인증에서 실패하면 역할의 신뢰 정책, 환경 이름, `main` 제한을 확인합니다. SSM 단계에서 실패하면 Managed nodes의 상태, 인스턴스 리전, 문서 버전/해시, 역할 권한을 확인합니다. 워크플로 로그에는 원시 AWS 오류나 서버 출력이 표시되지 않습니다. 필요하면 승인된 AWS 콘솔에서 해당 실행을 확인합니다.
- 실제 운영 전환은 별도 변경입니다. 승인된 main 이미지, HTTPS·SMTP·데이터 백업을 확인한 뒤 대기 앱 기동 → 가입·채팅 확인 → 트래픽 전환 → 실패 시 복귀 순으로 검증합니다. 이 역할과 문서는 운영 전환에 재사용하지 않습니다.
