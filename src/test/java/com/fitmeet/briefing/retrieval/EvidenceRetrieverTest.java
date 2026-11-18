package com.fitmeet.briefing.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fitmeet.briefing.ingestion.FilingDocumentRepository;
import com.fitmeet.briefing.ingestion.RawFilingStore;
import com.fitmeet.briefing.ingestion.SecFiling;
import com.fitmeet.briefing.ingestion.SecIssuer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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

class EvidenceRetrieverTest {
    @TempDir Path directory;
    private JdbcTemplate jdbc;
    private DriverManagerDataSource dataSource;
    private FilingDocumentRepository documents;
    private KeywordReadinessService readiness;
    private EvidenceRetriever retriever;
    private static final Instant CUTOFF = Instant.parse("2026-11-18T12:00:00Z");

    @BeforeEach
    void setup() {
        dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:retrieval-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V3__rag_source_documents.sql"))
                .execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        documents = new FilingDocumentRepository(jdbc);
        readiness = new KeywordReadinessService(jdbc);
        retriever = new EvidenceRetriever(jdbc);
    }

    @Test
    void pendingAndUnrelatedOrFutureFilingsNeverBecomeEvidence() throws Exception {
        save("CAMT", "0001109138", "0001109138-26-000001", "2026-11-17T12:00:00Z",
                "2026-11-17T12:05:00Z", "revenue climbed to 20 million");
        save("ABCD", "0001234567", "0001234567-26-000001", "2026-11-17T12:00:00Z",
                "2026-11-17T12:05:00Z", "revenue of another issuer");
        save("CAMT", "0001109138", "0001109138-26-000002", "2026-11-19T12:00:00Z",
                "2026-11-19T12:05:00Z", "future revenue");

        assertThat(retriever.search("CAMT", "revenue", CUTOFF, 7, 8)).isEmpty();
        assertThat(readiness.markPendingReady(10)).isEqualTo(3);
        var hits = retriever.search("CAMT", "revenue", CUTOFF, 7, 8);
        assertThat(hits).hasSize(1);
        assertThat(hits.getFirst().sourceUrl()).contains("www.sec.gov/Archives/edgar/data/1109138/");
        assertThat(hits.getFirst().rawSha256()).hasSize(64);
        assertThat(hits.getFirst().textSha256()).hasSize(64);
        assertThat(hits.getFirst().chunkId()).isPositive();
        assertThat(retriever.search("CAMT", "revenue", Instant.parse("2026-11-17T12:02:00Z"), 7, 8))
                .isEmpty(); // SEC had accepted the filing but the ingestion snapshot did not yet exist.
        assertThat(retriever.search("CAMT", "revenue", CUTOFF.minusSeconds(8 * 86400), 7, 8)).isEmpty();
    }

    @Test
    void pendingRevisionBlocksOlderReadyVersionUntilItIsVerified() throws Exception {
        var accession = "0001109138-26-000003";
        save("CAMT", "0001109138", accession, "2026-11-17T12:00:00Z",
                "2026-11-17T12:05:00Z", "revenue before correction");
        assertThat(readiness.markPendingReady(10)).isEqualTo(1);
        save("CAMT", "0001109138", accession, "2026-11-17T12:00:00Z",
                "2026-11-17T13:00:00Z", "revenue after correction");
        assertThat(retriever.search("CAMT", "revenue", CUTOFF, 7, 8)).isEmpty();
        assertThat(readiness.markPendingReady(10)).isEqualTo(1);
        assertThat(retriever.search("CAMT", "revenue", CUTOFF, 7, 8))
                .extracting(EvidenceRetriever.Evidence::text).containsExactly("revenue after correction");
    }

    @Test
    void invalidOrCorruptChunksCannotBeExposed() throws Exception {
        save("CAMT", "0001109138", "0001109138-26-000004", "2026-11-17T12:00:00Z",
                "2026-11-17T12:05:00Z", "revenue with damaged stored hash");
        jdbc.update("UPDATE rag_chunk SET text_sha256=?", "0".repeat(64));
        assertThatThrownBy(() -> readiness.markPendingReady(10)).isInstanceOf(IllegalStateException.class);
        assertThat(retriever.search("CAMT", "revenue", CUTOFF, 7, 8)).isEmpty();
        assertThatThrownBy(() -> retriever.search("CAMT", "%_", CUTOFF, 7, 8))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> retriever.search("CAMT' OR 1=1", "revenue", CUTOFF, 7, 8))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void springTransactionalBatchRollsBackEarlierReadyUpdatesOnLaterCorruption() throws Exception {
        save("CAMT", "0001109138", "0001109138-26-000006", "2026-11-17T12:00:00Z",
                "2026-11-17T12:05:00Z", "first valid revenue chunk");
        save("CAMT", "0001109138", "0001109138-26-000007", "2026-11-17T12:00:00Z",
                "2026-11-17T12:06:00Z", "second damaged revenue chunk");
        jdbc.update("UPDATE rag_chunk SET text_sha256=? WHERE document_id=(SELECT MAX(id) FROM rag_document)",
                "0".repeat(64));

        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(DriverManagerDataSource.class, () -> dataSource);
            context.register(TransactionalReadinessConfig.class);
            context.refresh();
            var proxiedService = context.getBean(KeywordReadinessService.class);
            assertThatThrownBy(() -> proxiedService.markPendingReady(10))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rag_document WHERE index_status='KEYWORD_READY'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rag_document WHERE index_status='PENDING'",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rag_chunk", Integer.class)).isEqualTo(2);
    }

    @Test
    void ranksMatchingTermsAndKeepsStableEvidenceIds() throws Exception {
        save("CAMT", "0001109138", "0001109138-26-000005", "2026-11-17T12:00:00Z",
                "2026-11-17T12:05:00Z", "revenue and guidance", "revenue only");
        readiness.markPendingReady(10);
        var hits = retriever.search("CAMT", "revenue guidance", CUTOFF, 7, 1);
        assertThat(hits).hasSize(1);
        assertThat(hits.getFirst().ordinal()).isZero();
        assertThat(hits.getFirst().accession()).isEqualTo("0001109138-26-000005");
        assertThat(hits.getFirst().acceptedAt()).isEqualTo(Instant.parse("2026-11-17T12:00:00Z"));
    }

    private void save(String symbol, String cik, String accession, String accepted, String fetched,
            String... chunks) throws Exception {
        var issuer = new SecIssuer(symbol, cik, Set.of("6-K"));
        var filing = new SecFiling(issuer, accession, "6-K", Instant.parse(accepted), "report.htm");
        var raw = new RawFilingStore().save(directory,
                String.join(" ", chunks).getBytes(StandardCharsets.UTF_8));
        assertThat(documents.save(filing, Instant.parse(fetched), raw, List.of(chunks))).isTrue();
    }

    @Configuration
    @EnableTransactionManagement
    static class TransactionalReadinessConfig {
        @Bean
        JdbcTemplate jdbcTemplate(DriverManagerDataSource source) { return new JdbcTemplate(source); }

        @Bean
        PlatformTransactionManager transactionManager(DriverManagerDataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean
        KeywordReadinessService keywordReadinessService(JdbcTemplate jdbcTemplate) {
            return new KeywordReadinessService(jdbcTemplate);
        }
    }
}
