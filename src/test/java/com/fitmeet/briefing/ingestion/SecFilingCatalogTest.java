package com.fitmeet.briefing.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SecFilingCatalogTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Instant now = Instant.parse("2026-11-17T12:00:00Z");

    @Test
    void selectsRecentOriginalAndAmendmentButNotFutureOrOtherForms() throws Exception {
        var submissions = mapper.readTree("""
            {"cik":1109138,"tickers":["CAMT"],"filings":{"recent":{
              "accessionNumber":["0001109138-26-000001","0001109138-26-000002","0001109138-26-000003","0001109138-26-000004"],
              "form":["6-K","6-K/A","4","20-F"],
              "acceptanceDateTime":["2026-11-10T12:00:00Z","2026-11-11T12:00:00Z","2026-11-12T12:00:00Z","2026-10-01T12:00:00Z"],
              "primaryDocument":["one.htm","amended.htm","four.htm","old.htm"]}}}
            """);
        var selected = SecFilingCatalog.select(submissions, now, 30, 5);
        assertThat(selected).extracting(SecFiling::form).containsExactly("6-K/A", "6-K");
        assertThat(selected.get(0).sourceUrl().toString())
                .isEqualTo("https://www.sec.gov/Archives/edgar/data/1109138/000110913826000002/amended.htm");
    }

    @Test
    void rejectsWrongIssuerAndMisalignedColumnsAndUnsafeDocument() throws Exception {
        var wrong = mapper.readTree("""
                {"cik":1109139,"tickers":["CAMT"],"filings":{"recent":{}}}
                """);
        assertThatThrownBy(() -> SecFilingCatalog.select(wrong, now, 30, 5))
                .isInstanceOf(IllegalArgumentException.class);
        var misaligned = mapper.readTree("""
                {"cik":1109138,"tickers":["CAMT"],"filings":{"recent":{
                  "accessionNumber":["0001109138-26-000001"],"form":[],
                  "acceptanceDateTime":[],"primaryDocument":[]}}}
                """);
        assertThatThrownBy(() -> SecFilingCatalog.select(misaligned, now, 30, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecFiling("0001109138-26-000001", "6-K", now, "../secrets.htm"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
