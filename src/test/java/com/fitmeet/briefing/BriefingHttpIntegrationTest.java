package com.fitmeet.briefing;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@TestPropertySource(properties = {
        "app.auth.allowed-origins=https://shockgraph-web.vercel.app",
        "app.briefing.upstream-origin=",
        "app.briefing.internal-token="
})
class BriefingHttpIntegrationTest {
    private static final String PATH = "/api/v1/briefings/CAMT";
    private static final String WEB = "https://shockgraph-web.vercel.app";

    @Autowired MockMvc mvc;

    @Test
    void anonymousReadReturnsHonestDisabledStateAndNoStore() throws Exception {
        mvc.perform(get(PATH).header("Origin", WEB).param("window", "7d"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", WEB))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.symbol").value("CAMT"))
                .andExpect(jsonPath("$.status").value("pending"));
        mvc.perform(get(PATH).param("window", "30d")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/briefings/UNKNOWN")).andExpect(status().isBadRequest());
        mvc.perform(post(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    void browserCorsOnlyAllowsConfiguredOriginAndReadMethod() throws Exception {
        mvc.perform(options(PATH).header("Origin", WEB)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk());
        mvc.perform(options(PATH).header("Origin", "https://untrusted.example.invalid")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
        mvc.perform(options(PATH).header("Origin", WEB)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
