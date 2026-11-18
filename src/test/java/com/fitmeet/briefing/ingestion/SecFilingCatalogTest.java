package com.fitmeet.briefing.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecFilingCatalogTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Instant now = Instant.parse("2026-11-17T12:00:00Z");
    private final SecIssuer camt = new SecIssuer("CAMT", "0001109138",
            Set.of("6-K", "6-K/A", "20-F", "20-F/A"));

    @Test
    void selectsRecentOriginalAndAmendmentButNotFutureOrOtherForms() throws Exception {
        var submissions = mapper.readTree("""
            {"cik":1109138,"tickers":["CAMT"],"filings":{"recent":{
              "accessionNumber":["0001109138-26-000001","0001109138-26-000002","0001109138-26-000003","0001109138-26-000004"],
              "form":["6-K","6-K/A","4","20-F"],
              "acceptanceDateTime":["2026-11-10T12:00:00Z","2026-11-11T12:00:00Z","2026-11-12T12:00:00Z","2026-10-01T12:00:00Z"],
              "primaryDocument":["one.htm","amended.htm","four.htm","old.htm"]}}}
            """);
        var selected = SecFilingCatalog.select(submissions, camt, now, 30, 5);
        assertThat(selected).extracting(SecFiling::form).containsExactly("6-K/A", "6-K");
        assertThat(selected.get(0).sourceUrl().toString())
                .isEqualTo("https://www.sec.gov/Archives/edgar/data/1109138/000110913826000002/amended.htm");
    }

    @Test
    void rejectsWrongIssuerAndMisalignedColumnsAndUnsafeDocument() throws Exception {
        var wrong = mapper.readTree("""
                {"cik":1109139,"tickers":["CAMT"],"filings":{"recent":{}}}
                """);
        assertThatThrownBy(() -> SecFilingCatalog.select(wrong, camt, now, 30, 5))
                .isInstanceOf(IllegalArgumentException.class);
        var misaligned = mapper.readTree("""
                {"cik":1109138,"tickers":["CAMT"],"filings":{"recent":{
                  "accessionNumber":["0001109138-26-000001"],"form":[],
                  "acceptanceDateTime":[],"primaryDocument":[]}}}
                """);
        assertThatThrownBy(() -> SecFilingCatalog.select(misaligned, camt, now, 30, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecFiling(camt, "0001109138-26-000001", "6-K", now, "../secrets.htm"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void configuredIssuerChangesBothEndpointIdentityAndFormPolicy() throws Exception {
        var other = new SecIssuer("ABCD", "0001234567", Set.of("8-K"));
        var submissions = mapper.readTree("""
            {"cik":1234567,"tickers":["ABCD"],"filings":{"recent":{
              "accessionNumber":["0001234567-26-000001","0001234567-26-000002"],
              "form":["8-K","6-K"],
              "acceptanceDateTime":["2026-11-10T12:00:00Z","2026-11-11T12:00:00Z"],
              "primaryDocument":["report.htm","foreign.htm"]}}}
            """);
        var selected = SecFilingCatalog.select(submissions, other, now, 30, 5);
        assertThat(selected).hasSize(1);
        assertThat(selected.get(0).sourceUrl().toString())
                .isEqualTo("https://www.sec.gov/Archives/edgar/data/1234567/000123456726000001/report.htm");
        assertThatThrownBy(() -> SecFilingCatalog.select(submissions, camt, now, 30, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecIssuer("../../bad", "0001234567", Set.of("8-K")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
