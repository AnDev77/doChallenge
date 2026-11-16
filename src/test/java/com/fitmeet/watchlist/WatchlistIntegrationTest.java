package com.fitmeet.watchlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitmeet.auth.domain.AuthenticatedMember;
import com.fitmeet.auth.domain.TokenProvider;
import com.fitmeet.member.domain.Member;
import com.fitmeet.member.domain.MemberRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
class WatchlistIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired WatchlistRepository entries;
    @MockBean TokenProvider tokens;

    @Test void ownerIsolationRepeatWritesAndUnsupportedSymbol() throws Exception {
        Long alice = member();
        Long bob = member();
        when(tokens.parseAccessToken("alice")).thenReturn(new AuthenticatedMember(alice, "alice@invalid.test",
                com.fitmeet.member.domain.MemberRole.USER));
        when(tokens.parseAccessToken("bob")).thenReturn(new AuthenticatedMember(bob, "bob@invalid.test",
                com.fitmeet.member.domain.MemberRole.USER));

        mvc.perform(get("/api/v1/watchlist")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/watchlist/QQQ")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/watchlist/QQQ").header("Authorization", "Bearer alice"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0]").value("QQQ"));
        mvc.perform(put("/api/v1/watchlist/QQQ").header("Authorization", "Bearer alice"))
                .andExpect(status().isOk());
        assertThat(entries.findByMemberIdOrderBySymbolAsc(alice)).hasSize(1);
        mvc.perform(put("/api/v1/watchlist/SPY").header("Authorization", "Bearer bob"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/watchlist").header("Authorization", "Bearer alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0]").value("QQQ"));
        mvc.perform(put("/api/v1/watchlist/OTHER").header("Authorization", "Bearer alice"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("WATCHLIST_001"));
        mvc.perform(delete("/api/v1/watchlist/QQQ").header("Authorization", "Bearer bob"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0]").value("SPY"));
        mvc.perform(delete("/api/v1/watchlist/QQQ").header("Authorization", "Bearer alice"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(delete("/api/v1/watchlist/QQQ").header("Authorization", "Bearer alice"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    private Long member() {
        return members.save(Member.createVerified(UUID.randomUUID() + "@invalid.test", "synthetic-hash", "fixture"))
                .getId();
    }
}
