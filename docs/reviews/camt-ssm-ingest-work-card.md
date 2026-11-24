# 2026-11-24 CAMT 운영 수집 자동화 작업 카드

- 목표: 운영 서버 터미널에서 반복 입력하지 않고 GitHub Actions에서 고정 CAMT SEC 수집·본문 확인을 실행한다.
- 시작 상태: main `b962053`; 공개 제공자 설정 PR #37, 30일 브리핑 PR #38은 열려 있다. 기존 SSM 점검은 고정 상태값만, ECR 후보는 별도 DB만 사용한다.
- 구현 범위: main 전용 수동 Actions 실행, OIDC 별도 역할, 문서 버전·해시 고정, SEC 30일 수집, 운영 MySQL `KEYWORD_READY` 청크의 제한된 출력. LLM·승인·운영 배포·프론트 연결은 제외한다.
- 완료 시험: Python 수집/출력/IAM/SSM 계약 모의 시험과 전체 `deploy/test_*.py`, 문서 렌더, `git diff --check`; 원격 PR 게이트 확인.
- 외부 입력: 한 번의 AWS CloudFormation 승인·배포와 GitHub 환경 변수/브랜치 제한, EC2의 `SEC_USER_AGENT` 설정. 실제 운영 실행은 이 설정과 병합 후에만 가능하다.
- 현재 판정: 코드·모의 시험 완료. EC2 수집·SEC 실자료 본문·실제 반복 실행은 미검증. 첫 실행 후 수집 건수, 접수/수집 시각과 SEC 링크를 검토해 종료 근거를 남긴다.
