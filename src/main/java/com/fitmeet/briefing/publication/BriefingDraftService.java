package com.fitmeet.briefing.publication;

import com.fitmeet.briefing.retrieval.EvidenceRetriever;
import com.fitmeet.briefing.retrieval.EvidenceRetriever.Evidence;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal draft/review boundary. It does not call a model or expose a public endpoint. */
@Service
public class BriefingDraftService {
    private final JdbcTemplate jdbc;
    private final EvidenceRetriever retriever;
    private final Clock clock = Clock.systemUTC();

    public BriefingDraftService(JdbcTemplate jdbc, EvidenceRetriever retriever) {
        this.jdbc = jdbc;
        this.retriever = retriever;
    }

    /** Select citations only from the same bounded, as-of retrieval that supplied the draft. */
    @Transactional
    public Draft create(String symbol, String query, Instant asOf, String summary, List<Long> citedChunkIds) {
        if (summary == null || summary.isBlank() || summary.length() > 4000
                || citedChunkIds == null || citedChunkIds.isEmpty() || citedChunkIds.size() > 8
                || citedChunkIds.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(citedChunkIds).size() != citedChunkIds.size()) {
            throw new IllegalArgumentException("Invalid draft or citation IDs");
        }
        if (asOf == null) throw new IllegalArgumentException("Missing as-of time");
        Instant snapshot = asOf.truncatedTo(ChronoUnit.MICROS); // MySQL DATETIME(6) precision.
        var candidates = retriever.search(symbol, query, snapshot, 7, 8);
        var cited = new ArrayList<Evidence>();
        for (long id : citedChunkIds) {
            cited.add(candidates.stream().filter(e -> e.chunkId() == id).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Citation outside retrieved evidence")));
        }
        cited.sort(Comparator.comparingLong(Evidence::chunkId));
        String digest = digest(symbol, snapshot, summary, cited.stream().map(Source::from).toList());
        var created = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        var holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO briefing_draft (symbol, as_of, summary, content_sha256, status, created_at)
                    VALUES (?,?,?,?, 'DRAFT', ?)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, symbol);
            statement.setObject(2, LocalDateTime.ofInstant(snapshot, ZoneOffset.UTC));
            statement.setString(3, summary);
            statement.setString(4, digest);
            statement.setObject(5, created);
            return statement;
        }, holder);
        long draftId = holder.getKey().longValue();
        for (var evidence : cited) {
            jdbc.update("""
                    INSERT INTO briefing_draft_evidence
                      (draft_id, chunk_id, document_id, text_sha256, raw_sha256, source_url, accepted_at, fetched_at)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, draftId, evidence.chunkId(), evidence.documentId(), evidence.textSha256(),
                    evidence.rawSha256(), evidence.sourceUrl(),
                    LocalDateTime.ofInstant(evidence.acceptedAt(), ZoneOffset.UTC),
                    LocalDateTime.ofInstant(evidence.fetchedAt(), ZoneOffset.UTC));
        }
        return load(draftId).orElseThrow();
    }

    /** The reviewer must submit the hash of the exact draft they inspected. */
    @Transactional
    public Draft approve(long draftId, String expectedSha256, String reviewer) {
        if (expectedSha256 == null || !expectedSha256.matches("[0-9a-f]{64}")
                || reviewer == null || reviewer.isBlank() || reviewer.length() > 100) {
            throw new IllegalArgumentException("Invalid review request");
        }
        Draft draft = load(draftId).orElseThrow(() -> new IllegalArgumentException("Draft not found"));
        if (!draft.status().equals("DRAFT") || !draft.contentSha256().equals(expectedSha256)
                || !digest(draft.symbol(), draft.asOf(), draft.summary(), draft.sources()).equals(expectedSha256)) {
            throw new IllegalStateException("Draft changed or already reviewed");
        }
        int changed = jdbc.update("""
                UPDATE briefing_draft SET status='APPROVED', reviewer=?, reviewed_at=?
                WHERE id=? AND status='DRAFT' AND content_sha256=?
                """, reviewer, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), draftId, expectedSha256);
        if (changed != 1) throw new IllegalStateException("Draft was reviewed concurrently");
        return load(draftId).orElseThrow();
    }

    public Optional<Draft> load(long draftId) {
        var rows = jdbc.query("""
                SELECT id, symbol, as_of, summary, content_sha256, status, reviewer
                  FROM briefing_draft WHERE id=?
                """, (rs, row) -> new Draft(rs.getLong(1), rs.getString(2),
                rs.getObject(3, LocalDateTime.class).toInstant(ZoneOffset.UTC), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), List.of()), draftId);
        return rows.stream().findFirst().map(row -> new Draft(row.id(), row.symbol(), row.asOf(),
                row.summary(), row.contentSha256(), row.status(), row.reviewer(), sources(row.id())));
    }

    /** Only a reviewed version is eligible for a later Java public response adapter. */
    public Optional<Draft> latestApproved(String symbol) {
        var ids = jdbc.query("""
                SELECT id FROM briefing_draft WHERE symbol=? AND status='APPROVED'
                ORDER BY reviewed_at DESC, id DESC LIMIT 1
                """, (rs, row) -> rs.getLong(1), symbol);
        var approved = ids.isEmpty() ? Optional.<Draft>empty() : load(ids.getFirst());
        approved.ifPresent(draft -> {
            if (!digest(draft.symbol(), draft.asOf(), draft.summary(), draft.sources())
                    .equals(draft.contentSha256())) {
                throw new IllegalStateException("Approved draft snapshot changed");
            }
        });
        return approved;
    }

    private List<Source> sources(long draftId) {
        return jdbc.query("""
                SELECT chunk_id, document_id, text_sha256, raw_sha256, source_url, accepted_at, fetched_at
                  FROM briefing_draft_evidence WHERE draft_id=? ORDER BY chunk_id
                """, (rs, row) -> new Source(rs.getLong(1), rs.getLong(2), rs.getString(3),
                rs.getString(4), rs.getString(5),
                rs.getObject(6, LocalDateTime.class).toInstant(ZoneOffset.UTC),
                rs.getObject(7, LocalDateTime.class).toInstant(ZoneOffset.UTC)), draftId);
    }

    private static String digest(String symbol, Instant asOf, String summary, List<Source> sources) {
        try {
            var hash = MessageDigest.getInstance("SHA-256");
            add(hash, symbol);
            add(hash, asOf.toString());
            add(hash, summary);
            for (Source source : sources) {
                add(hash, Long.toString(source.chunkId()));
                add(hash, Long.toString(source.documentId()));
                add(hash, source.textSha256());
                add(hash, source.rawSha256());
                add(hash, source.sourceUrl());
                add(hash, source.acceptedAt().toString());
                add(hash, source.fetchedAt().toString());
            }
            return java.util.HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void add(MessageDigest hash, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        hash.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        hash.update(bytes);
    }

    public record Source(long chunkId, long documentId, String textSha256, String rawSha256,
                         String sourceUrl, Instant acceptedAt, Instant fetchedAt) {
        static Source from(Evidence evidence) {
            return new Source(evidence.chunkId(), evidence.documentId(), evidence.textSha256(),
                    evidence.rawSha256(), evidence.sourceUrl(), evidence.acceptedAt(), evidence.fetchedAt());
        }
    }

    public record Draft(long id, String symbol, Instant asOf, String summary, String contentSha256,
                        String status, String reviewer, List<Source> sources) {}
}
