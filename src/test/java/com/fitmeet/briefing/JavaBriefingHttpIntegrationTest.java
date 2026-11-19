package com.fitmeet.briefing;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitmeet.briefing.publication.BriefingDraftService;
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
        when(drafts.latestApproved("CAMT")).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/briefings/CAMT").param("window", "7d"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.status").value("insufficient_sources"))
                .andExpect(jsonPath("$.summary").doesNotExist());
        mvc.perform(get("/api/v1/briefings/CAMT").param("window", "30d"))
                .andExpect(status().isBadRequest());
    }
}
