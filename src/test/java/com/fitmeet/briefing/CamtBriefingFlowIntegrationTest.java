package com.fitmeet.briefing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitmeet.briefing.generation.BriefingGenerationService;
import com.fitmeet.briefing.ingestion.FilingDocumentRepository;
import com.fitmeet.briefing.ingestion.RawFilingStore;
import com.fitmeet.briefing.ingestion.SecFiling;
import com.fitmeet.briefing.ingestion.SecIssuer;
import com.fitmeet.briefing.publication.BriefingDraftService;
import com.fitmeet.briefing.retrieval.EvidenceRetriever;
import com.fitmeet.briefing.retrieval.KeywordReadinessService;
import com.fitmeet.instruments.InstrumentCatalog;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Synthetic SEC-shaped CAMT input through the actual Java retrieval, draft and public GET chain. */
class CamtBriefingFlowIntegrationTest {
    @TempDir Path directory;

    @Test
    void freshlyCollectedOlderFilingRequiresAnExplicitWindowAndReview() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:camt-window-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V3__rag_source_documents.sql"),
                new ClassPathResource("db/migration/V4__briefing_draft_review.sql")).execute(source);
        var jdbc = new JdbcTemplate(source);
        var issuer = new SecIssuer("CAMT", "0001109138", Set.of("6-K"));
        var accepted = Instant.now().minusSeconds(20L * 86400);
        var filing = new SecFiling(issuer, "0001109138-26-000002", "6-K", accepted, "report.htm");
        var raw = new RawFilingStore().save(directory, "revenue evidence".getBytes(StandardCharsets.UTF_8));
        assertThat(new FilingDocumentRepository(jdbc).save(filing, Instant.now().minusSeconds(60), raw,
                List.of("revenue evidence"))).isTrue();
        assertThat(new KeywordReadinessService(jdbc).markPendingReady(10)).isEqualTo(1);
        long chunkId = jdbc.queryForObject("SELECT id FROM rag_chunk", Long.class);
        var retriever = new EvidenceRetriever(jdbc);
        var drafts = new BriefingDraftService(jdbc, retriever);
        var calls = new AtomicInteger();
        var generator = new BriefingGenerationService(retriever, drafts, (instruction, input) -> {
            calls.incrementAndGet();
            return "{\"summary\":\"공시의 매출 관련 내용을 확인했다.\",\"citation_ids\":[" + chunkId + "]}";
        }, new ObjectMapper());
        var mvc = MockMvcBuilders.standaloneSetup(new BriefingController(null,
                new JavaBriefingPublication(drafts, new ObjectMapper()), "java")).build();

        assertThatThrownBy(() -> generator.generate("CAMT", "revenue", Instant.now()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("No eligible evidence");
        assertThat(calls).hasValue(0);
        var draft = generator.generate("CAMT", "revenue", Instant.now(), 30);
        mvc.perform(get("/api/v1/briefings/CAMT").param("window", "30d"))
                .andExpect(jsonPath("$.status").value("insufficient_sources"));
        drafts.approve(draft.id(), draft.contentSha256(), "synthetic-operator");
        mvc.perform(get("/api/v1/briefings/CAMT").param("window", "7d"))
                .andExpect(jsonPath("$.status").value("insufficient_sources"));
        mvc.perform(get("/api/v1/briefings/CAMT").param("window", "30d"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.freshness").value("stale"))
                .andExpect(jsonPath("$.citations[0].url").value(filing.sourceUrl().toString()));
        assertThat(calls).hasValue(1);
    }

    @Test
    void camtEvidenceMustBeReviewedBeforeThePublicEndpointShowsTheWebContract() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:camt-flow-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V3__rag_source_documents.sql"),
                new ClassPathResource("db/migration/V4__briefing_draft_review.sql")).execute(source);
        var jdbc = new JdbcTemplate(source);
        var issuer = new SecIssuer("CAMT", "0001109138", Set.of("6-K"));
        var filing = new SecFiling(issuer, "0001109138-26-000001", "6-K",
                Instant.now().minusSeconds(3600), "report.htm");
        var raw = new RawFilingStore().save(directory, "revenue evidence".getBytes(StandardCharsets.UTF_8));
        assertThat(new FilingDocumentRepository(jdbc).save(filing, Instant.now().minusSeconds(1800),
                raw, List.of("revenue evidence"))).isTrue();
        assertThat(new KeywordReadinessService(jdbc).markPendingReady(10)).isEqualTo(1);
        long chunkId = jdbc.queryForObject("SELECT id FROM rag_chunk", Long.class);

        var retriever = new EvidenceRetriever(jdbc);
        var drafts = new BriefingDraftService(jdbc, retriever);
        var modelCalls = new AtomicInteger();
        var generator = new BriefingGenerationService(retriever, drafts, (instruction, input) -> {
            modelCalls.incrementAndGet();
            return "{\"summary\":\"공시의 매출 관련 내용을 확인했다.\",\"citation_ids\":[" + chunkId + "]}";
        }, new ObjectMapper());
        var mvc = MockMvcBuilders.standaloneSetup(new BriefingController(null,
                new JavaBriefingPublication(drafts, new ObjectMapper()), "java")).build();

        assertThat(new InstrumentCatalog("java").list().getFirst().briefing()).isTrue();
        mvc.perform(get("/api/v1/briefings/CAMT").param("window", "7d"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("insufficient_sources"));
        var draft = generator.generate("CAMT", "revenue", Instant.now());
        mvc.perform(get("/api/v1/briefings/CAMT").param("window", "7d"))
                .andExpect(jsonPath("$.status").value("insufficient_sources"));

        drafts.approve(draft.id(), draft.contentSha256(), "synthetic-operator");
        mvc.perform(get("/api/v1/briefings/CAMT").param("window", "7d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.summary[0].text").value("공시의 매출 관련 내용을 확인했다."))
                .andExpect(jsonPath("$.summary[0].citation_ids[0]").value(Long.toString(chunkId)))
                .andExpect(jsonPath("$.citations[0].url").value(filing.sourceUrl().toString()));
        assertThat(modelCalls).hasValue(1); // The public GET never performs a model call.
    }
}
