package com.fitmeet.briefing.operator;

import com.fitmeet.briefing.generation.BriefingGenerationService;
import com.fitmeet.briefing.publication.BriefingDraftService;
import com.fitmeet.briefing.publication.BriefingDraftService.Draft;
import com.fitmeet.briefing.publication.BriefingDraftService.Source;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** An opt-in, local, one-shot operator command. It is never mapped to HTTP. */
@Component
@ConditionalOnProperty(prefix = "app.briefing.operator", name = "action")
public class BriefingOperatorRunner implements ApplicationRunner {
    private final Environment environment;
    private final BriefingGenerationService generation;
    private final BriefingDraftService drafts;
    private final JdbcTemplate jdbc;

    public BriefingOperatorRunner(Environment environment, BriefingGenerationService generation,
                                  BriefingDraftService drafts, JdbcTemplate jdbc) {
        this.environment = environment;
        this.generation = generation;
        this.drafts = drafts;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        execute(System.out);
    }

    void execute(PrintStream out) {
        if (!"none".equals(environment.getProperty("spring.main.web-application-type"))) {
            throw new IllegalStateException("Operator commands require a non-web application");
        }
        if (Boolean.parseBoolean(environment.getProperty("app.briefing.ingestion.run-once", "false"))) {
            throw new IllegalStateException("Run SEC ingestion separately before operator commands");
        }
        String action = required("app.briefing.operator.action");
        switch (action) {
            case "generate" -> {
                if ("none".equals(environment.getProperty("spring.ai.model.chat", "none"))) {
                    throw new IllegalStateException("Configure a briefing chat provider before generation");
                }
                String symbol = required("app.briefing.operator.symbol");
                String query = required("app.briefing.operator.query");
                int lookbackDays = Integer.parseInt(environment.getProperty(
                        "app.briefing.operator.lookback-days", "7"));
                Draft draft = generation.generate(symbol, query, Instant.now(), lookbackDays);
                out.println("Created DRAFT " + draft.id() + "; inspect it before approval");
            }
            case "inspect" -> {
                Draft draft = load();
                out.println("DRAFT ID: " + draft.id() + " | STATUS: " + draft.status());
                out.println("SYMBOL: " + draft.symbol() + " | AS_OF: " + draft.asOf());
                out.println("SUMMARY: " + draft.summary());
                for (Source source : draft.sources()) {
                    out.println("SOURCE chunk=" + source.chunkId() + " url=" + source.sourceUrl());
                    out.println("accepted=" + source.acceptedAt() + " fetched=" + source.fetchedAt());
                    out.println("TEXT: " + verifiedText(source));
                }
                out.println("CONTENT_SHA256: " + draft.contentSha256());
            }
            case "approve" -> {
                long id = draftId();
                String hash = required("app.briefing.operator.sha256");
                String reviewer = required("app.briefing.operator.reviewer");
                String confirmation = required("app.briefing.operator.confirmation");
                if (!confirmation.equals("APPROVE:" + id + ":" + hash)) {
                    throw new IllegalArgumentException("Confirmation must include the inspected draft ID and hash");
                }
                Draft draft = drafts.load(id).orElseThrow(() -> new IllegalArgumentException("Draft not found"));
                if (!draft.contentSha256().equals(hash) || draft.sources().isEmpty()) {
                    throw new IllegalStateException("Draft changed or has no evidence");
                }
                draft.sources().forEach(this::verifiedText);
                Draft approved = drafts.approve(id, hash, reviewer);
                out.println("Approved draft " + approved.id() + " for " + approved.symbol());
            }
            default -> throw new IllegalArgumentException("Unknown briefing operator action");
        }
    }

    private Draft load() {
        return drafts.load(draftId()).orElseThrow(() -> new IllegalArgumentException("Draft not found"));
    }

    private long draftId() {
        long id = Long.parseLong(required("app.briefing.operator.draft-id"));
        if (id <= 0) throw new IllegalArgumentException("Draft ID must be positive");
        return id;
    }

    private String required(String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return value;
    }

    private String verifiedText(Source source) {
        var rows = jdbc.query("""
                SELECT c.section_text, c.text_sha256, d.raw_sha256, d.source_url,
                       d.accepted_at, d.fetched_at
                FROM rag_chunk c JOIN rag_document d ON d.id=c.document_id
                WHERE c.id=? AND d.id=?
                """, (rs, row) -> new EvidenceRow(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getObject(5, java.time.LocalDateTime.class).toInstant(java.time.ZoneOffset.UTC),
                rs.getObject(6, java.time.LocalDateTime.class).toInstant(java.time.ZoneOffset.UTC)),
                source.chunkId(), source.documentId());
        if (rows.size() != 1) throw new IllegalStateException("Draft evidence is missing");
        EvidenceRow row = rows.getFirst();
        if (!row.textSha256().equals(source.textSha256()) || !sha256(row.text()).equals(source.textSha256())
                || !row.rawSha256().equals(source.rawSha256()) || !row.sourceUrl().equals(source.sourceUrl())
                || !row.acceptedAt().equals(source.acceptedAt()) || !row.fetchedAt().equals(source.fetchedAt())) {
            throw new IllegalStateException("Draft evidence changed");
        }
        return row.text();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private record EvidenceRow(String text, String textSha256, String rawSha256, String sourceUrl,
                               Instant acceptedAt, Instant fetchedAt) {}
}
