# GitHub Actions → EC2 연결 확인

- **시작, 2026-11-12 KST:** doChallenge 원격 main `0b9e926`에서 분리했다. #15의 로컬 진단, #17의 IP TLS와 Nginx 자동 갱신, #18의 배포 후 장애 기록은 별도 변경이다. 사용자는 공인 IP HTTPS 시험 성공을 보고했다. 그 시험과 Actions의 AWS 권한은 별개다.
- **중간:** PR에서는 네트워크 없는 시험만 하고, AWS 권한은 수동 `main` 작업에만 준다. SSM 문서는 입력을 받지 않고 Docker/Compose와 체크아웃/env의 존재 여부만 반환한다. 이름·버전·해시, 대상 노드, 실행 ID, 반환 상태를 검증한다. AWS CLI 오류·서버 비허용 출력을 그대로 로깅하지 않는다.
- **완료 범위:** 워크플로 1개, 고정 SSM 문서 1개, 실행기 1개, 시험 파일 1개, 초기 설정 안내와 이 리뷰를 추가했다. 로컬 신규 시험 6개는 정확한 SSM 대상과 인자, 잘못된 설정 차단, 타 노드/실패 응답 거부, 누락·중복·허용 외 출력 차단, AWS 오류 비노출, 합성 파일의 존재 판정을 확인한다.
- **오류·한계:** 로컬 Java 전체 시험/JAR은 Gradle 8.5 다운로드가 네트워크에서 막혀 실행되지 않았다. 워크플로 구문·권한 분리와 diff 검사는 로컬에서 통과했다. 실제 AWS OIDC, SSM 문서 등록, GitHub 환경 변수와 EC2 첫 호출은 이 작업에서 설정하지 않았다. 합성 결과를 접속 성공으로 사용하지 않는다. Java 동작, DB, Redis, SMTP, 인증서, 기존 컨테이너, Vercel 설정을 변경하지 않는다. 운영 전환·복귀는 이 PR 범위 밖이다.

직접 볼 코드: `.github/workflows/ec2-ssm-preflight.yml`, `deploy/ssm/read-only-preflight.json`, `deploy/ssm_probe.py`, `deploy/test_ssm_probe.py`. 설정 순서: `deploy/ssm/README.md`.
