# EC2 배포 후보 자동 기동

`EC2 ECR candidate`는 같은 실행에서 Java 21 전체 시험·Redis·JAR을 확인하고 Docker 이미지를 빌드해 내부 API를 검사한다. 수동 `main` 실행에서만 검사한 이미지 자체를 ECR에 업로드하고, 그 digest를 고정한 SSM 문서로 EC2에 전달한다. PR의 시험에는 AWS 권한이 없다.

## 1. 최초 한 번: GitHub와 AWS 연결

1. GitHub **Settings → Environments**에서 `ec2-candidate-publish`와 `ec2-candidate`를 생성한다. 둘 다 **Selected branches and tags → Branch → main**만 허용한다. 브랜치 제한이 없다면 AWS 역할을 활성화하지 않는다.
2. AWS 오하이오(`us-east-2`)에서 현재 EC2가 SSM 온라인이며 **linux/amd64**, SSM Agent **3.3.2746.0 이상**, AWS CLI v2, Docker Compose의 `up --wait`, Python 3, OpenSSL, flock을 사용할 수 있는지 확인한다. EC2 프로필이 이 인스턴스 전용인지 확인한다. 동일 역할을 쓰는 다른 인스턴스에도 아래의 ECR 읽기 권한이 부여된다.
3. 검토한 버전의 저장소에서 아래 명령을 사용한다. 이 명령은 AWS CLI와 IAM/CloudFormation 권한이 있는 **CloudShell 등 관리 단말**에서 실행한다. GitHub OIDC 공급자는 [기존 SSM 연결](../ssm/README.md)의 것을 재사용한다. 인스턴스 ID와 EC2에 붙어 있는 IAM **역할 이름**을 확인하고 아래 명령의 자리표시자를 바꾼다. GitHub용 조회 역할을 넣지 않는다.

   ```bash
   python3 -B deploy/candidate/render_stack.py > /tmp/shockgraph-candidate-stack.json
   aws cloudformation deploy --region us-east-2 \
     --stack-name shockgraph-ec2-candidate \
     --template-file /tmp/shockgraph-candidate-stack.json \
     --parameter-overrides InstanceId=i-REPLACE Ec2RoleName=REPLACE \
     --capabilities CAPABILITY_IAM
   ```

   지정된 후보 ECR과 SSM 문서, GitHub용 두 역할을 만든다. EC2 역할에는 후보 저장소를 읽는 권한만 추가한다. 같은 이름의 리소스가 이미 다른 목적으로 존재하면 소유권을 확인한다. 승인된 SSM 문서는 버전 1과 해당 해시로 고정되며, 이후 명령 변경은 별도 버전과 승인으로 다룬다. ECR 이미지 저장 비용이 생길 수 있다.

4. 두 출력과 SSM 문서의 해시를 조회한다.

   ```bash
   aws cloudformation describe-stacks --region us-east-2 \
     --stack-name shockgraph-ec2-candidate \
     --query 'Stacks[0].Outputs[*].[OutputKey,OutputValue]' --output table

   aws ssm describe-document --region us-east-2 --name ShockGraph-StartCandidateV1 \
     --document-version 1 --query 'Document.[Status,HashType,Hash]' --output text
   ```

   문서 상태 `Active`, 해시 종류 `Sha256`을 확인한다.

   | GitHub 환경 | 변수 | 값 |
   |---|---|---|
   | `ec2-candidate-publish` | `AWS_CANDIDATE_PUBLISH_ROLE_ARN` | `PublishRoleArn` |
   | `ec2-candidate` | `AWS_CANDIDATE_RUN_ROLE_ARN` | `RunRoleArn` |
   | `ec2-candidate` | `EC2_INSTANCE_ID` | `TargetInstanceId` |
   | `ec2-candidate` | `AWS_CANDIDATE_DOCUMENT_SHA256` | AWS가 계산한 문서 버전 1의 해시 |

## 2. 실행과 결과

승인된 변경이 `main`에 들어간 뒤 **Actions → EC2 ECR candidate → Run workflow → main**을 실행한다. `EC2 candidate verified`는 Java health, QQQ·SPY JSON 조회, 비로그인 쓰기 거부가 **EC2 내부**에서 확인됐음을 뜻한다. 실패하면 출력된 단계와 AWS SSM 실행 상태를 확인한다. 타임아웃 후에는 원래 명령이 종료됐는지 확인한 다음 재실행한다. 겹치는 호스트 실행은 파일 잠금으로 거부한다.

후보는 `shockgraph-ecr-candidate`라는 별도 Compose 프로젝트, 외부 포트가 없는 내부 네트워크, 별도 MySQL 볼륨을 사용한다. 로컬 전용 합성 DB·JWT 값은 첫 실행에 자동 생성해 루트 전용 경로에 보존한다. 실제 SMTP는 사용하지 않고 네트워크도 분리한다. 첫 기동과 재실행에서 같은 후보 볼륨을 유지한다. 기존 `shockgraph-tls-check`, 운영 `.env`, Nginx, 공개 포트, 운영 DB는 변경하지 않는다.

후보 실패는 자동으로 운영 롤백을 수행하지 않는다. 운영으로 보낸 트래픽이 없고, 실패한 후보 컨테이너는 진단을 위해 남을 수 있다. 호스트 재기동 후 필요하면 워크플로를 다시 실행한다. 공개 HTTPS와 CORS는 별도 검증하고, 실 SMTP·운영 데이터·Blue/Green 전환/복귀는 후속 작업이다.
