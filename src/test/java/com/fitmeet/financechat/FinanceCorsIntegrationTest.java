package com.fitmeet.financechat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
@TestPropertySource(properties = "app.auth.allowed-origins=https://shockgraph-web.vercel.app")
class FinanceCorsIntegrationTest {
    private static final String PATH = "/api/v1/finance/rooms/QQQ/messages";
    private static final String ORIGIN = "https://shockgraph-web.vercel.app";

    @Autowired MockMvc mvc;

    @Test void publicReadPreflightAllowsOnlyConfiguredWebOrigin() throws Exception {
        mvc.perform(options(PATH).header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
        mvc.perform(options(PATH).header("Origin", "https://untrusted.example.invalid")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test void authenticatedWritePreflightAllowsAuthorizationHeader() throws Exception {
        mvc.perform(options(PATH).header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
    }
}
