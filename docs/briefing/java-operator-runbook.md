# Java 브리핑 초안 생성·검토: 로컬 실행 절차

2026-11-20 기준. 이 명령은 서버의 JAR를 **비웹 프로세스**로 별도 실행한다. 공개 HTTP에는 생성·검토·승인 엔드포인트가 없다. 운영자가 사용할 DB 연결 정보와 공급자 키는 서버의 비밀 설정에서 주입한다. `BRIEFING_CHAT_PROVIDER=none`이 기본값이므로 실제 호출은 설정 전까지 실행되지 않는다.

## 순서

1. SEC 수집을 먼저 별도 run-once로 실행해 CAMT 문서를 `KEYWORD_READY`로 만든다. SEC `SEC_USER_AGENT`와 저장 디렉터리 및 DB 연결을 확인한다. 수집 프로세스와 아래 명령을 같은 실행에 섞지 않는다.
2. 생성 명령을 실행한다. 예: `BRIEFING_CHAT_PROVIDER=google-genai GEMINI_API_KEY=... java -jar app.jar --spring.main.web-application-type=none --app.briefing.operator.action=generate --app.briefing.operator.symbol=CAMT --app.briefing.operator.query=revenue`. 키는 명령행 인자로 넣지 않는다. 근거가 없으면 모델 호출 없이 실패하고, 성공해도 `DRAFT` ID만 출력한다.
3. `java -jar app.jar --spring.main.web-application-type=none --app.briefing.operator.action=inspect --app.briefing.operator.draft-id=<ID>`로 요약과 정확히 인용된 청크 본문, SEC 원문 URL, 접수·수집 시각, 내용 해시를 확인한다. 인용 내용과 요약의 사실·숫자·종목 일치 여부를 사람이 SEC 원문과 대조한다. 잘못됐으면 승인하지 않고 원인을 고쳐 다시 생성한다.
4. 검토자가 동일한 ID와 해시를 명시해 별도의 비웹 프로세스에서 승인한다. `java -jar app.jar --spring.main.web-application-type=none --app.briefing.operator.action=approve --app.briefing.operator.draft-id=<ID> --app.briefing.operator.sha256=<64자리 해시> --app.briefing.operator.reviewer=<운영자 식별자> --app.briefing.operator.confirmation=APPROVE:<ID>:<64자리 해시>`. 승인 시 저장된 근거의 텍스트 해시·메타데이터를 다시 검사하고, 초안 해시·상태를 조건부 갱신한다. 두 번째 승인은 거부된다.
5. 운영 GET을 Java 모드로 선택하려면 별도로 `BRIEFING_PUBLIC_PROVIDER=java`를 설정한다. 승인된 종목만 `ready`; 승인 전에는 `insufficient_sources`다. 실제 외부 공개 전 브라우저 계약·기준 시각·원문 링크를 확인한다.

## CAMT 공시가 7일보다 오래됐을 때 (2026-11-24)

새 수집은 `fetched_at`을 갱신하지만 SEC의 `accepted_at`은 바꾸지 않는다. 11월 4일 접수 공시를 11월 24일 수집하면 기본 7일 생성·조회에는 포함되지 않는다. 운영자가 근거 기간을 명시해 시험할 때만 `--app.briefing.operator.lookback-days=30`으로 초안을 생성하고, 승인 결과는 Java 공개 모드에서 `?window=30d`로 조회한다. 기본 `?window=7d`는 이전 공시를 최근 7일 자료로 보이지 않는다. `30d` 결과는 근거 접수일이 7일보다 오래됐으면 `freshness=stale`이다. Python 공개 모드는 기존 7일 계약만 허용한다.

EC2 단일 Compose에서 먼저 원문·청크만 수집하고 DB 본문을 조회할 수 있다. SEC에 식별 가능한 이름과 연락처를 입력하되 그 값을 이 문서나 로그에 남기지 않는다. 원문 저장 경로는 저장소 밖에 두고 실행 사용자 UID로 쓰게 한다. 아래 명령은 외부 모델을 호출하거나 초안을 승인하지 않는다.

```bash
cd ~/doChallenge
mkdir -p "$HOME/shockgraph-sec-filings"
chmod 700 "$HOME/shockgraph-sec-filings"
read -r -p 'SEC User-Agent (name and contact email): ' SEC_USER_AGENT
sudo docker compose --env-file deploy/.env -f deploy/compose.yaml run --rm --no-deps \
  --user "$(id -u):$(id -g)" \
  --volume "$HOME/shockgraph-sec-filings:/app/data/sec-filings" \
  -e SEC_USER_AGENT="$SEC_USER_AGENT" api \
  --spring.main.web-application-type=none \
  --app.briefing.ingestion.run-once=true \
  --app.briefing.ingestion.lookback-days=30
unset SEC_USER_AGENT
sudo docker compose --env-file deploy/.env -f deploy/compose.yaml exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" --batch' <<'SQL'
SELECT d.accession, d.accepted_at, d.fetched_at, d.index_status,
       d.source_url, c.ordinal_no, LEFT(c.section_text, 1200) AS excerpt
FROM rag_document d JOIN rag_chunk c ON c.document_id=d.id
WHERE d.symbol='CAMT'
ORDER BY d.accepted_at DESC, c.ordinal_no LIMIT 5;
SQL
```

수집 출력의 새 문서·`KEYWORD_READY` 건수와 SQL 본문을 확인한 뒤에만 별도 Gemini 생성·`inspect`·사람 승인으로 진행한다. Java 공개 제공자 설정 PR과 이번 기간 선택 변경이 모두 병합·배포되기 전에는 운영 `30d` 응답 성공으로 계산하지 않는다. SEC 수집 0건, 네트워크 실패, 파서 오류는 실자료 성공으로 계산하지 않는다.

운영자 식별자는 서버 접근 권한을 가진 사람이 입력하는 기록값이다. 이 CLI는 OS 로그인 신원과 식별자의 일치를 증명하지 않으므로, 공유 셸을 통한 검토·웹 사용자의 자기 승인을 허용하는 경로로 간주하지 않는다. 독립적인 인증·권한·감사 UI와 품질 평가 세트는 후속 작업이다. `inspect` 출력에는 공개 SEC 본문이 포함되므로 터미널 출력의 보존·공유 범위를 확인한다. 실제 키·DB·SEC 실자료가 없는 테스트는 운영 승인이나 공개 검증으로 계산하지 않는다.
