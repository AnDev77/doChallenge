# 2026-11-26 이미지 릴리스 작업 카드

- 시작: main b76f93a, 열린 #40은 의도적 결함 실습이므로 포함하지 않는다. 사용자 요청은 로컬 빌드/업로드와 EC2 pull 전환이다.
- 목표: 기존 ECR 재사용, 커밋별 이미지와 digest 고정, 로컬 원격 실행 CLI, 실패 시 기존 이미지 복구.
- 시험: 입력/명령/실패 복구 단위 시험, production Compose와 smoke overlay, 원격 Java 21/Redis/JAR/Compose/BlueGreen 게이트.
- 외부 입력: 로컬 AWS 프로필과 SSH alias, 기존 ECR/IAM 최초 설정, EC2에 병합된 배포 스크립트 반영. 실제 원격 교체는 미실행.
- 종료 기준: 코드/CI와 실제 운영 적용 결과를 따로 기록한다. 운영 실증은 사용자 인증 경로 준비 후 수행한다.
