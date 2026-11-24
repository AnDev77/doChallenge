package com.fitmeet.briefing;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitmeet.briefing.publication.BriefingDraftService;
import com.fitmeet.briefing.publication.BriefingDraftService.Draft;
import com.fitmeet.briefing.publication.BriefingDraftService.Source;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@TestPropertySource(properties = "app.briefing.public-provider=java")
class JavaBriefingHttpIntegrationTest {
    @Autowired MockMvc mvc;
    @MockBean BriefingDraftService drafts;

    @Test
    void anonymousJavaReadDoesNotExposeUnapprovedDraft() throws Exception {
        mvc.perform(get("/api/v1/instruments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].symbol").value("CAMT"))
                .andExpect(jsonPath("$.data[0].briefing").value(true))
                .andExpect(jsonPath("$.data[1].briefing").value(false));
        when(drafts.latestApproved("CAMT")).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/briefings/CAMT").param("window", "7d"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.status").value("insufficient_sources"))
                .andExpect(jsonPath("$.summary").doesNotExist());
        mvc.perform(get("/api/v1/briefings/CAMT").param("window", "30d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("insufficient_sources"));
    }

    @Test
    void reviewedCamtDraftUsesTheWebContractWithItsSecCitation() throws Exception {
        Instant asOf = Instant.now().minusSeconds(60);
        Instant accepted = asOf.minusSeconds(3600);
        var source = new Source(42L, 7L, "a".repeat(64), "b".repeat(64),
                "https://www.sec.gov/Archives/edgar/data/1109138/filing.htm", accepted, accepted.plusSeconds(30));
        when(drafts.latestApproved("CAMT")).thenReturn(Optional.of(new Draft(1L, "CAMT", asOf,
                "공시의 매출 관련 내용을 확인했다.", "c".repeat(64), "APPROVED", "operator", List.of(source))));
        mvc.perform(get("/api/v1/briefings/CAMT").param("window", "7d")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.summary[0].citation_ids[0]").value("42"))
                .andExpect(jsonPath("$.citations[0].url").value(source.sourceUrl()))
                .andExpect(jsonPath("$.stance").value("withheld"));
    }
}
