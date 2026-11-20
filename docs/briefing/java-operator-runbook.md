# Java 브리핑 초안 생성·검토: 로컬 실행 절차

2026-11-20 기준. 이 명령은 서버의 JAR를 **비웹 프로세스**로 별도 실행한다. 공개 HTTP에는 생성·검토·승인 엔드포인트가 없다. 운영자가 사용할 DB 연결 정보와 공급자 키는 서버의 비밀 설정에서 주입한다. `BRIEFING_CHAT_PROVIDER=none`이 기본값이므로 실제 호출은 설정 전까지 실행되지 않는다.

## 순서

1. SEC 수집을 먼저 별도 run-once로 실행해 CAMT 문서를 `KEYWORD_READY`로 만든다. SEC `SEC_USER_AGENT`와 저장 디렉터리 및 DB 연결을 확인한다. 수집 프로세스와 아래 명령을 같은 실행에 섞지 않는다.
2. 생성 명령을 실행한다. 예: `BRIEFING_CHAT_PROVIDER=google-genai GEMINI_API_KEY=... java -jar app.jar --spring.main.web-application-type=none --app.briefing.operator.action=generate --app.briefing.operator.symbol=CAMT --app.briefing.operator.query=revenue`. 키는 명령행 인자로 넣지 않는다. 근거가 없으면 모델 호출 없이 실패하고, 성공해도 `DRAFT` ID만 출력한다.
3. `java -jar app.jar --spring.main.web-application-type=none --app.briefing.operator.action=inspect --app.briefing.operator.draft-id=<ID>`로 요약과 정확히 인용된 청크 본문, SEC 원문 URL, 접수·수집 시각, 내용 해시를 확인한다. 인용 내용과 요약의 사실·숫자·종목 일치 여부를 사람이 SEC 원문과 대조한다. 잘못됐으면 승인하지 않고 원인을 고쳐 다시 생성한다.
4. 검토자가 동일한 ID와 해시를 명시해 별도의 비웹 프로세스에서 승인한다. `java -jar app.jar --spring.main.web-application-type=none --app.briefing.operator.action=approve --app.briefing.operator.draft-id=<ID> --app.briefing.operator.sha256=<64자리 해시> --app.briefing.operator.reviewer=<운영자 식별자> --app.briefing.operator.confirmation=APPROVE:<ID>:<64자리 해시>`. 승인 시 저장된 근거의 텍스트 해시·메타데이터를 다시 검사하고, 초안 해시·상태를 조건부 갱신한다. 두 번째 승인은 거부된다.
5. 운영 GET을 Java 모드로 선택하려면 별도로 `BRIEFING_PUBLIC_PROVIDER=java`를 설정한다. 승인된 종목만 `ready`; 승인 전에는 `insufficient_sources`다. 실제 외부 공개 전 브라우저 계약·기준 시각·원문 링크를 확인한다.

운영자 식별자는 서버 접근 권한을 가진 사람이 입력하는 기록값이다. 이 CLI는 OS 로그인 신원과 식별자의 일치를 증명하지 않으므로, 공유 셸을 통한 검토·웹 사용자의 자기 승인을 허용하는 경로로 간주하지 않는다. 독립적인 인증·권한·감사 UI와 품질 평가 세트는 후속 작업이다. `inspect` 출력에는 공개 SEC 본문이 포함되므로 터미널 출력의 보존·공유 범위를 확인한다. 실제 키·DB·SEC 실자료가 없는 테스트는 운영 승인이나 공개 검증으로 계산하지 않는다.
