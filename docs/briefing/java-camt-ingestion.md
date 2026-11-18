# Java SEC 공시 수집: CAMT 첫 설정과 확장 경계

상태: 2026-11-17 구현 PR. 현재는 문서 준비 단계이며 RAG 검색·LLM·Kafka·공개 API 전환은 포함하지 않는다.

## 처리 순서

1. `SecIngestionRunner`는 `app.briefing.ingestion.run-once=true`일 때만 생성된다. API 기동의 기본 경로에서 SEC 호출은 없다. 운영자는 SEC에 식별 가능한 연락처를 포함한 `SEC_USER_AGENT`와 지속되는 `BRIEFING_RAW_DIRECTORY`를 설정한다. 서버 시작 인자로 `--app.briefing.ingestion.run-once=true`를 준다. DB에는 V3 마이그레이션이 먼저 적용돼야 한다.
2. `application.yml`의 `issuers` 목록은 첫 대상 CAMT의 `symbol`, `cik`, `forms`를 설정한다. `IngestionProperties`가 목록을 바인딩하고 `SecIssuer`가 식별자와 허용 서식을 검증한다. 다음 종목은 공식 SEC CIK·티커 관계를 확인해 새 항목을 추가한다. 티커별 저장소·코드를 복사하지 않는다. 추가 종목이 곧 공개 브리핑 지원을 뜻하지는 않는다.
3. `SecClient.submissions`는 설정된 CIK의 `data.sec.gov/submissions/CIK{cik}.json`을 요청한다. 짧은 접속·응답 시간 제한, 리다이렉트 금지, 최대 4 MB, JSON MIME을 강제한다. 입력 JSON의 임의 URL을 방문하지 않는다.
4. `SecFilingCatalog.select`는 응답 CIK와 설정 티커가 함께 일치하는지 확인한다. recent 배열의 열 길이가 같아야 한다. 수집 시각 이전, 기본 최근 30일 안의 설정된 공시 서식 중 종목별 최대 5건을 접수 시각 내림차순으로 고른다. 발표일과 SEC 접수 시각은 다르므로 SEC의 `acceptanceDateTime`을 기준으로 기록한다.
5. `SecFiling.sourceUrl`은 검증된 CIK·accession과 안전한 HTML 파일명만으로 `www.sec.gov/Archives/edgar/data/{cik}/...` URL을 직접 만든다. `SecClient.document`는 HTML MIME과 4 MB 상한을 확인한다. 리다이렉트, 외부 호스트, 경로 문자 주입을 허용하지 않는다.
6. `FilingChunker`는 HTML을 텍스트로 정리하면서 숨겨진 요소와 실행 스크립트를 제거한다. 표는 행과 열을 ` | `로 구분해 단위와 값의 이웃 관계를 보존한다. 최대 1,000개, 각 최대 1,500자의 청크를 만든다. 표·수치에 대한 사실 검증과 임베딩은 이후 단계다.
7. `RawFilingStore`는 다운로드한 **원본 바이트**를 SHA-256 키 아래 HTML 파일로 보존한다. 기존 동일 키의 내용이 다르면 실패한다. `FilingDocumentRepository`는 종목·CIK·원문 해시, SEC URL, 서식·접수·수집 시각, 파서·청크 버전과 청크를 MySQL에 저장한다. `index_status=PENDING`이라 아직 검색 대상이 아니다.

## 재실행·정정·실패

- 동일 종목·accession·파일명·원문 해시는 DB 유일 제약으로 한 번만 기록한다. SEC에 정정 공시가 새 accession으로 올라오거나 같은 URL의 원문 바이트가 달라지면 기존 문서를 수정하지 않고 새 문서 버전을 만든다. 새로운 원문을 검증된 이전 결과에 몰래 대체하지 않는다.
- 다운로드, MIME, 문서 구조, 크기 또는 DB 쓰기가 실패하면 실행 전체가 실패한다. 잘못된 원문은 `rag_document`에 부분 공개되지 않는다. 원문 파일 기록 후 DB가 실패하면 해시 파일이 남을 수 있다. 같은 입력으로 재시작하면 이 파일을 재사용하고 DB 기록을 다시 시도한다.
- 파일 저장과 MySQL 기록은 하나의 트랜잭션이 아니다. 파일 시스템은 백업·마운트가 필요한 개발용 어댑터다. 컨테이너 내부 임시 디스크로 운영하지 않는다. S3 호환 객체 저장소, 색인 완료 상태, 문서별 실패·재시도 큐는 후속 구현이다.
- 실제 CAMT 2026-11-04 6-K의 SEC 목록과 HTML 접근을 개발 환경에서 읽기 전용으로 확인했다. 이 날짜는 11/17 기준 최근 7일 밖이므로 오늘의 최신 공개 브리핑 자료로 취급하지 않는다. 이번 PR에서는 설정된 SEC 연락처로 Java 수집기를 종단 실행하거나 운영 DB에 쓰지 않았다.
- SEC 목록에 티커가 없거나 설정 CIK와 일치하지 않으면 전체 수집을 실패시켜 다른 회사 공시를 섞지 않는다. 파일 형식은 아직 HTML만 허용한다. PDF·XBRL은 별도 파서와 시험을 추가한 뒤 켠다.

## 이어질 작업

저장된 문서 버전에서 검색 기준선·임베딩 색인을 만들고, 근거 ID로 LLM 초안을 검증한 다음 사람 검토를 거쳐 공개한다. 그 흐름이 동작한 후 작업 생성과 outbox·Kafka 소비를 붙인다. GET 브리핑 API는 이 단계에서 변경하지 않는다.
