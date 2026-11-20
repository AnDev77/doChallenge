package com.fitmeet.briefing.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitmeet.briefing.JavaBriefingPublication;
import com.fitmeet.briefing.generation.BriefingGenerationService;
import com.fitmeet.briefing.ingestion.FilingDocumentRepository;
import com.fitmeet.briefing.ingestion.RawFilingStore;
import com.fitmeet.briefing.ingestion.SecFiling;
import com.fitmeet.briefing.ingestion.SecIssuer;
import com.fitmeet.briefing.publication.BriefingDraftService;
import com.fitmeet.briefing.retrieval.EvidenceRetriever;
import com.fitmeet.briefing.retrieval.KeywordReadinessService;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class BriefingOperatorRunnerTest {
    @TempDir Path directory;

    @Test
    void separateGenerateInspectAndExplicitApprovalControlsPublicResult() throws Exception {
        Fixture fixture = new Fixture(directory);
        long chunk = fixture.chunk();
        MockEnvironment env = fixture.env("generate").withProperty("spring.ai.model.chat", "google-genai")
                .withProperty("app.briefing.operator.symbol", "CAMT")
                .withProperty("app.briefing.operator.query", "revenue");
        BriefingOperatorRunner runner = fixture.runner(env, chunk);
        String generated = output(runner);
        assertThat(generated).contains("Created DRAFT");
        long draftId = fixture.jdbc.queryForObject("SELECT id FROM briefing_draft", Long.class);
        var publication = new JavaBriefingPublication(fixture.drafts, new ObjectMapper());
        assertThat(publication.lookup("CAMT").path("status").asText()).isEqualTo("insufficient_sources");

        env.setProperty("app.briefing.operator.action", "inspect");
        env.setProperty("app.briefing.operator.draft-id", Long.toString(draftId));
        String inspection = output(runner);
        var draft = fixture.drafts.load(draftId).orElseThrow();
        assertThat(inspection).contains(draft.summary(), "TEXT: revenue evidence", draft.contentSha256());

        env.setProperty("app.briefing.operator.action", "approve");
        env.setProperty("app.briefing.operator.sha256", draft.contentSha256());
        env.setProperty("app.briefing.operator.reviewer", "operator-one");
        env.setProperty("app.briefing.operator.confirmation", "APPROVE:" + draftId + ":" + draft.contentSha256());
        assertThat(output(runner)).contains("Approved draft " + draftId);
        assertThat(publication.lookup("CAMT").path("status").asText()).isEqualTo("ready");
        assertThatThrownBy(() -> output(runner)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already reviewed");
    }

    @Test
    void unsafeExecutionAndChangedEvidenceFailBeforeApproval() throws Exception {
        Fixture fixture = new Fixture(directory);
        long chunk = fixture.chunk();
        var draft = fixture.drafts.create("CAMT", "revenue", Instant.now(), "매출 관련 공시 근거를 검토한다.", List.of(chunk));
        MockEnvironment env = fixture.env("approve")
                .withProperty("app.briefing.operator.draft-id", Long.toString(draft.id()))
                .withProperty("app.briefing.operator.sha256", draft.contentSha256())
                .withProperty("app.briefing.operator.reviewer", "operator-one")
                .withProperty("app.briefing.operator.confirmation", "APPROVE:" + draft.id() + ":" + draft.contentSha256());
        BriefingOperatorRunner runner = fixture.runner(env, chunk);
        env.setProperty("spring.main.web-application-type", "servlet");
        assertThatThrownBy(() -> output(runner)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-web");
        env.setProperty("spring.main.web-application-type", "none");
        env.setProperty("app.briefing.ingestion.run-once", "true");
        assertThatThrownBy(() -> output(runner)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("separately");
        env.setProperty("app.briefing.ingestion.run-once", "false");
        env.setProperty("app.briefing.operator.confirmation", "APPROVE:wrong");
        assertThatThrownBy(() -> output(runner)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Confirmation");
        env.setProperty("app.briefing.operator.confirmation", "APPROVE:" + draft.id() + ":" + draft.contentSha256());
        fixture.jdbc.update("UPDATE rag_chunk SET section_text='tampered' WHERE id=?", chunk);
        assertThatThrownBy(() -> output(runner)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("evidence changed");
        assertThat(fixture.drafts.load(draft.id()).orElseThrow().status()).isEqualTo("DRAFT");
    }

    private static String output(BriefingOperatorRunner runner) {
        var bytes = new ByteArrayOutputStream();
        runner.execute(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static final class Fixture {
        final JdbcTemplate jdbc;
        final BriefingDraftService drafts;
        final Path directory;

        Fixture(Path directory) {
            this.directory = directory;
            var db = new DriverManagerDataSource("jdbc:h2:mem:operator-" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V3__rag_source_documents.sql"),
                    new ClassPathResource("db/migration/V4__briefing_draft_review.sql")).execute(db);
            jdbc = new JdbcTemplate(db);
            drafts = new BriefingDraftService(jdbc, new EvidenceRetriever(jdbc));
        }

        long chunk() throws Exception {
            var issuer = new SecIssuer("CAMT", "0001109138", Set.of("6-K"));
            var filing = new SecFiling(issuer, "0001109138-26-000001", "6-K",
                    Instant.now().minusSeconds(3600), "report.htm");
            var raw = new RawFilingStore().save(directory, "revenue evidence".getBytes(StandardCharsets.UTF_8));
            new FilingDocumentRepository(jdbc).save(filing, Instant.now().minusSeconds(1800), raw,
                    List.of("revenue evidence"));
            new KeywordReadinessService(jdbc).markPendingReady(10);
            return jdbc.queryForObject("SELECT id FROM rag_chunk", Long.class);
        }

        MockEnvironment env(String action) {
            return new MockEnvironment().withProperty("spring.main.web-application-type", "none")
                    .withProperty("app.briefing.operator.action", action);
        }

        BriefingOperatorRunner runner(MockEnvironment env, long chunk) {
            var generation = new BriefingGenerationService(new EvidenceRetriever(jdbc), drafts,
                    (system, input) -> "{\"summary\":\"매출 관련 공시 근거를 검토한다.\",\"citation_ids\":[" + chunk + "]}",
                    new ObjectMapper());
            return new BriefingOperatorRunner(env, generation, drafts, jdbc);
        }
    }
}
