package com.fitmeet.briefing.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fitmeet.briefing.ingestion.FilingDocumentRepository;
import com.fitmeet.briefing.ingestion.RawFilingStore;
import com.fitmeet.briefing.ingestion.SecFiling;
import com.fitmeet.briefing.ingestion.SecIssuer;
import com.fitmeet.briefing.retrieval.EvidenceRetriever;
import com.fitmeet.briefing.retrieval.KeywordReadinessService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

class BriefingDraftServiceTest {
    @TempDir Path directory;
    private static final Instant AS_OF = Instant.parse("2026-11-18T12:00:00.123456789Z");

    @Test
    void draftRequiresRetrievedCitationAndExplicitVersionedApproval() throws Exception {
        var source = database();
        var jdbc = new JdbcTemplate(source);
        var documents = new FilingDocumentRepository(jdbc);
        var issuer = new SecIssuer("CAMT", "0001109138", Set.of("6-K"));
        var filing = new SecFiling(issuer, "0001109138-26-000001", "6-K",
                Instant.parse("2026-11-17T12:00:00Z"), "report.htm");
        var raw = new RawFilingStore().save(directory, "revenue guidance".getBytes(StandardCharsets.UTF_8));
        assertThat(documents.save(filing, Instant.parse("2026-11-17T12:05:00Z"), raw,
                List.of("revenue guidance"))).isTrue();
        long chunkId = jdbc.queryForObject("SELECT id FROM rag_chunk", Long.class);

        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(DriverManagerDataSource.class, () -> source);
            context.register(Services.class);
            context.refresh();
            var service = context.getBean(BriefingDraftService.class);
            assertThatThrownBy(() -> service.create("CAMT", "revenue", AS_OF,
                    "Unreviewed summary", List.of(chunkId))).isInstanceOf(IllegalArgumentException.class);
            assertThat(new KeywordReadinessService(jdbc).markPendingReady(10)).isEqualTo(1);
            assertThatThrownBy(() -> service.create("CAMT", "revenue", AS_OF,
                    "Unreviewed summary", List.of(chunkId + 1000))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.create("CAMT", "revenue", AS_OF,
                    "Unreviewed summary", List.of(chunkId, chunkId))).isInstanceOf(IllegalArgumentException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM briefing_draft", Integer.class)).isZero();

            var draft = service.create("CAMT", "revenue", AS_OF, "Unreviewed summary", List.of(chunkId));
            assertThat(draft.asOf()).isEqualTo(Instant.parse("2026-11-18T12:00:00.123456Z"));
            assertThat(draft.sources()).hasSize(1);
            assertThat(draft.sources().getFirst().rawSha256()).hasSize(64);
            assertThat(service.latestApproved("CAMT")).isEmpty();
            assertThatThrownBy(() -> service.approve(draft.id(), "0".repeat(64), "reviewer"))
                    .isInstanceOf(IllegalStateException.class);

            var approved = service.approve(draft.id(), draft.contentSha256(), "reviewer");
            assertThat(approved.status()).isEqualTo("APPROVED");
            assertThat(service.latestApproved("CAMT")).contains(approved);
            assertThat(service.latestApproved("SPY")).isEmpty();
            assertThatThrownBy(() -> service.approve(draft.id(), draft.contentSha256(), "reviewer"))
                    .isInstanceOf(IllegalStateException.class);

            var modified = service.create("CAMT", "revenue", AS_OF, "A second draft", List.of(chunkId));
            jdbc.update("UPDATE briefing_draft SET summary='Changed after review display' WHERE id=?", modified.id());
            assertThatThrownBy(() -> service.approve(modified.id(), modified.contentSha256(), "reviewer"))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(service.latestApproved("CAMT")).contains(approved);
            jdbc.update("UPDATE briefing_draft_evidence SET raw_sha256=? WHERE draft_id=?",
                    "0".repeat(64), approved.id());
            assertThatThrownBy(() -> service.latestApproved("CAMT"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void fabricatedHashCannotApproveAnUnverifiedDraft() {
        var source = database();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("""
                INSERT INTO briefing_draft (symbol, as_of, summary, content_sha256, status, created_at)
                VALUES ('CAMT', CURRENT_TIMESTAMP, 'original', ?, 'DRAFT', CURRENT_TIMESTAMP)
                """, "1".repeat(64));
        var service = new BriefingDraftService(jdbc, new EvidenceRetriever(jdbc));
        assertThatThrownBy(() -> service.approve(1L, "1".repeat(64), "reviewer"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM briefing_draft WHERE id=1", String.class))
                .isEqualTo("DRAFT");
    }

    private static DriverManagerDataSource database() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:publication-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V3__rag_source_documents.sql"),
                new ClassPathResource("db/migration/V4__briefing_draft_review.sql")).execute(source);
        return source;
    }

    @Configuration
    @EnableTransactionManagement
    static class Services {
        @Bean JdbcTemplate jdbc(DriverManagerDataSource source) { return new JdbcTemplate(source); }
        @Bean PlatformTransactionManager transactionManager(DriverManagerDataSource source) {
            return new DataSourceTransactionManager(source);
        }
        @Bean EvidenceRetriever retriever(JdbcTemplate jdbc) { return new EvidenceRetriever(jdbc); }
        @Bean BriefingDraftService draftService(JdbcTemplate jdbc, EvidenceRetriever retriever) {
            return new BriefingDraftService(jdbc, retriever);
        }
    }
}
