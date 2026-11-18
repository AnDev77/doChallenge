# Java 공시 검색 기준선: 코드가 실행되는 순서와 안전 경계

기준일: 2026-11-18. 대상: `feat/java-briefing-retrieval`. 이 기능은 **내부 근거 후보 검색**이다. 지금 공개 `GET /api/v1/briefings/{symbol}`은 여전히 `BriefingGateway`를 거쳐 Python으로 간다. LLM 생성, 사람 검토, Kafka, 임베딩 색인은 아직 연결되지 않았다.

## 0. 전체 호출 흐름

| 순서 | 호출·DB 변경 | 그 시점의 보장 |
|---|---|---|
| 1 | 운영자가 `run-once=true`로 실행 → `SecIngestionRunner.run` → `FilingIngestionService.collect` | 평소 API 기동에서는 SEC 호출이나 검색 준비를 하지 않는다. |
| 2 | SEC 목록·HTML 검증 → `FilingChunker.chunks` → `RawFilingStore.save` → `FilingDocumentRepository.save` | 원본 파일 SHA-256 경로와 MySQL 문서·청크 저장. DB 문서는 우선 `PENDING`; 청크 INSERT까지 한 DB 트랜잭션이다. 파일 저장은 이 트랜잭션 밖이다. |
| 3 | 수집 완료 → `KeywordReadinessService.markPendingReady(100)`을 남은 문서가 없을 때까지 반복 | 각 배치에서 청크 검사 후에만 `KEYWORD_READY`로 변경. 기존 `PENDING` 문서도 다음 수집 실행에서 재검사한다. |
| 4 | 향후 브리핑 생성 코드가 `EvidenceRetriever.search(...)` 호출 | 준비된 문서의 해당 시점에 이용 가능한 청크만 내부 근거 후보로 반환. 현재 이 메서드를 호출하는 생성 서비스는 없다. |

`KEYWORD_READY`는 MySQL 청크를 **키워드 검색에 노출할 수 있음**을 뜻한다. 임베딩을 생성했거나 Qdrant에 저장했다는 뜻이 아니다.

## 1. `EvidenceRetriever.search`: 두 시각과 원문 버전

정확한 컬럼 이름은 `rag_document.accepted_at`, `fetched_at`, `document_name`이다. `accepted_at`은 SEC 접수 시각, `fetched_at`은 우리 수집 시각이다. 둘 다 DB에서는 UTC `DATETIME(6)`이며 Java의 `asOf`는 `Instant`를 UTC `LocalDateTime`으로 변환해 바인딩한다. 결과를 읽을 때도 UTC로 되돌린다.

```sql
WHERE d.symbol = :symbol
  AND d.index_status = 'KEYWORD_READY'
  AND d.accepted_at >= :asOfMinusLookback
  AND d.accepted_at <= :asOf
  AND d.fetched_at <= :asOf
```

예를 들어 11월 17일 23:00 UTC에 접수했지만 18일 01:00에 우리가 저장했다면 `asOf=17일 23:30`에는 검색되지 않는다. 18일 02:00에는 적격해진다. `accepted_at <= asOf`만 검사하면 사후에 수집된 자료를 과거 리포트에 소급해서 쓸 수 있으므로 두 시각이 필요하다. 단, 이 조건이 **실제 시장의 모든 참가자에게 언제 이용 가능했는지**를 증명하는 것은 아니다. 우리 시스템의 저장 이력에 대한 시점 격리다.

원문 바이트가 바뀌어 같은 `symbol + accession + document_name`으로 새 `rag_document` 행이 생기면 `raw_sha256`이 다른 **원문 버전**이다. 다음 `NOT EXISTS`는 `asOf`까지 수집된 버전 중 더 늦게 수집된 행이 있으면 이전 행을 제외한다. 같은 `fetched_at`이면 큰 DB `id`가 나중 버전이다.

```sql
AND NOT EXISTS (
  SELECT 1 FROM rag_document newer
  WHERE newer.symbol = d.symbol
    AND newer.accession = d.accession
    AND newer.document_name = d.document_name
    AND newer.fetched_at <= :asOf
    AND (newer.fetched_at > d.fetched_at
         OR (newer.fetched_at = d.fetched_at AND newer.id > d.id))
)
```

여기서 `newer.index_status`를 제한하지 않은 것이 핵심이다. 이전 버전은 `KEYWORD_READY`이고 최신 버전은 `PENDING`이면, 이전 버전도 `NOT EXISTS`에서 걸려 숨겨진다. 잠시 근거가 0건이 되더라도 이미 교체된 원문을 인용하지 않기 위한 선택이다. `asOf`가 새 버전의 `fetched_at`보다 과거라면 이전 버전은 그 과거 검색에서 계속 보일 수 있다. 검색 결과의 `documentId`, `chunkId`, `rawSha256`, `textSha256`, `sourceUrl`, `accession`, 두 시각은 나중에 근거를 재현하고 인용 ID를 대조하는 재료다. **해시와 ID만으로 근거 문장의 의미가 맞는지는 검증되지 않는다.**

**SEC의 공식 수정 공시와 구별:** 일반적으로 6-K/A 같은 수정 제출은 *새 accession*을 갖는다. 위 `NOT EXISTS`는 새 accession과 옛 accession의 관계를 찾지 않는다. 즉 현재 숨김은 **같은 accession·파일명의 원문 바이트 변경**에만 적용된다. 공식 amendment가 이전 공시를 대체하는지 판단해 기존 공시까지 숨기는 기능은 별도 관계 모델과 검증이 필요하다. 이를 이미 구현한 것처럼 설명하지 않는다.

## 2. `KeywordReadinessService.markPendingReady`: 무엇을 언제 검사하는가

`FilingDocumentRepository.save`는 문서/청크를 DB에 저장하고 `index_status='PENDING'`으로 둔다. 준비 메서드는 최대 100개의 `PENDING` 문서 ID를 순서대로 읽고 각 문서의 청크를 `ordinal_no` 순서로 검사한다.

1. 청크가 1~1,000개인지 검사한다. 0개는 검색할 내용이 없고, 1,000개 초과는 파서의 상한을 벗어난다.
2. 순서가 `0, 1, 2, ...`인지 검사한다. 중간 번호가 사라지면 그 문서를 완전한 근거로 보지 않는다.
3. 본문이 비어 있지 않고 길이가 1,500자 이하인지 검사한다. `SHA-256(UTF-8(section_text))`를 계산해 `text_sha256`과 대조한다. 길이 대조는 **1,500자 상한 검사**이지 과거 길이 메타데이터와 1:1 비교가 아니다. 별도의 길이 메타데이터는 저장하지 않는다.
4. 모두 통과하면 `UPDATE ... SET index_status='KEYWORD_READY' WHERE id=? AND index_status='PENDING'`를 실행한다. 이미 준비된 행은 다시 처리하지 않는다.

`@Transactional`은 Spring이 주입한 서비스 빈을 `SecIngestionRunner`에서 외부 호출할 때 적용된다. **한 `markPendingReady(100)` 호출이 하나의 DB 트랜잭션**이다. 예를 들어 첫 문서 상태 변경 후 두 번째 문서의 해시가 틀려 예외가 나면 첫 문서의 `KEYWORD_READY` 변경도 롤백된다. 다음 배치 호출이나 이미 완료한 이전 배치까지 되돌리는 전역 트랜잭션은 아니다. `FilingDocumentRepository.save`가 먼저 커밋한 원문 메타데이터·청크도 이 롤백으로 삭제되지 않는다. 해시 파일은 애초에 MySQL 트랜잭션에 참여하지 않는다.

손상된 청크는 다시 실행한다고 자동 수정되지 않는다. 그 문서는 `PENDING`으로 남고 같은 실패가 반복될 수 있으므로 원인 확인·복구가 필요하다. 이번 H2 테스트에는 Spring 트랜잭션 프록시로 첫 변경 후 두 번째 검사 실패를 주입해 두 상태가 모두 `PENDING`으로 남는 사례를 추가했다. 테스트 코드를 작성한 것과 CI에서 실제 통과한 것은 구분한다.

## 3. `LIKE`와 512개: 검색·부하의 실제 의미

검색어를 소문자로 바꾸고 Unicode 문자·숫자로 이뤄진 길이 2 이상의 단어를 최대 8개 추출한다. 각 단어를 `LOWER(c.section_text) LIKE '%word%'`의 **바인딩 값**으로 넣고 OR로 결합한다. 종목·시각·최신 버전 조건에 맞고 단어 하나라도 포함한 청크를 `accepted_at DESC, document_id DESC, ordinal_no`로 **DB가 먼저 정렬**한 뒤 최대 512건만 Java로 받는다. Java는 그 512건에서 일치하는 *서로 다른 단어 수*가 많은 순서, 접수 시각, 문서 ID, 청크 순서로 정렬해 최대 `topK=8`건을 돌려준다.

**중요한 한계:** `LIMIT 512`는 DB가 Java로 반환하는 후보 수를 제한한다. `%word%`와 `LOWER(column)` 조건 자체를 인덱스로 빠르게 만들거나 **전체 스캔을 막는 장치는 아니다.** 따라서 DB 검색 비용이 데이터 증가에도 일정하다고 주장할 수 없다. 최근 문서의 일치 청크가 512건을 채우면 오래됐지만 더 관련성이 높은 청크는 Java 순위 계산에 참여하지 못한다. 단순 부분 문자열이라 `unit`과 `community`도 구별하지 못하고, 영문 SEC 문서와 한국어 질의의 의미 대응도 없다. 이 결과를 기준선으로 측정한 뒤, 임베딩 검색·인덱스·재정렬과 동일한 평가 집합에서 비교해야 한다.

입력은 티커 형식, 검색어 최대 256자, 기간 1~365일, `topK` 1~8로 제한한다. 단어 추출과 SQL 파라미터 바인딩은 임의 SQL 및 와일드카드가 질의 구조를 바꾸지 않도록 한다. 이 내부 메서드는 사용자용 엔드포인트가 아니다.

## 4. 검증 상태와 다음 단계

`EvidenceRetrieverTest`는 V3 스키마의 H2 MySQL 모드에서 종목 분리, 접수·수집 시간 경계, 같은 파일의 두 원문 버전, 최신 `PENDING` 시 구버전 숨김, 해시 손상, 검색 순위와 근거 ID를 검사한다. 추가 테스트는 Spring 트랜잭션 프록시를 통해 배치 롤백을 확인한다. 로컬 Gradle 배포 파일 다운로드는 네트워크에 막혔다. **PR #29 코드 커밋 `0bdbdc6`의 원격 Java 21 전체 시험·실행 JAR, Compose, Blue/Green, 스키마 게이트는 성공했다.** 이 문서 기록 커밋의 원격 게이트는 별도로 확인한다. 실제 SEC→Java→MySQL 수집, 실 MySQL 지연·쿼리 플랜, 원문 파일 재검사, 고정 근거 평가, 벡터 검색, LLM·사람 검토, Kafka, 공개 GET 전환은 미검증/미구현이다.
