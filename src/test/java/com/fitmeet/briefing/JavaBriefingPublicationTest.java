package com.fitmeet.briefing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitmeet.briefing.publication.BriefingDraftService;
import com.fitmeet.briefing.publication.BriefingDraftService.Draft;
import com.fitmeet.briefing.publication.BriefingDraftService.Source;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JavaBriefingPublicationTest {
    private final BriefingDraftService drafts = mock(BriefingDraftService.class);
    private final JavaBriefingPublication publication = new JavaBriefingPublication(drafts, new ObjectMapper());

    @Test
    void approvedDraftMatchesWebContractAndProvidesOnlyItsCitations() {
        var now = Instant.now();
        var source = new Source(41, 7, "text-hash", "raw-hash",
                "https://www.sec.gov/Archives/edgar/data/1109138/report.htm", now.minusSeconds(3600), now);
        when(drafts.latestApproved("CAMT")).thenReturn(Optional.of(new Draft(1, "CAMT", now,
                "공시 내용을 검토했다.", "hash", "APPROVED", "reviewer", List.of(source))));

        var json = publication.lookup("CAMT");
        assertThat(json.path("status").asText()).isEqualTo("ready");
        assertThat(json.path("stance").asText()).isEqualTo("withheld");
        assertThat(json.path("summary").get(0).path("citation_ids").get(0).asText()).isEqualTo("41");
        assertThat(json.path("citations").get(0).path("id").asText()).isEqualTo("41");
        assertThat(json.path("citations").get(0).path("url").asText()).startsWith("https://www.sec.gov/");
        assertThat(json.path("bullish_factors").isEmpty()).isTrue();
    }

    @Test
    void missingApprovalOrUnsafeSourceDoesNotPublishText() {
        assertThat(publication.lookup("CAMT").path("status").asText()).isEqualTo("insufficient_sources");
        var now = Instant.now();
        var bad = new Source(41, 7, "text-hash", "raw-hash", "https://evil.example/report", now, now);
        when(drafts.latestApproved("CAMT")).thenReturn(Optional.of(new Draft(1, "CAMT", now,
                "비공개 초안", "hash", "APPROVED", "reviewer", List.of(bad))));
        var response = publication.lookup("CAMT");
        assertThat(response.path("status").asText()).isEqualTo("failed");
        assertThat(response.has("summary")).isFalse();
    }
}
