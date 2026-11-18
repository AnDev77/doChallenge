package com.fitmeet.briefing.retrieval;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Bounded lexical retrieval baseline. Only keyword-ready, available SEC versions are returned. */
@Service
public class EvidenceRetriever {
    private static final Pattern SYMBOL = Pattern.compile("[A-Z][A-Z0-9.]{0,11}");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]{2,}");
    private static final int CANDIDATE_LIMIT = 512;
    private final JdbcTemplate jdbc;

    public EvidenceRetriever(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Evidence> search(String symbol, String query, Instant asOf, int lookbackDays, int topK) {
        if (symbol == null || !SYMBOL.matcher(symbol).matches() || asOf == null
                || lookbackDays < 1 || lookbackDays > 365 || topK < 1 || topK > 8
                || query == null || query.length() > 256) throw new IllegalArgumentException("Invalid retrieval request");
        var matcher = WORD.matcher(query.toLowerCase(Locale.ROOT));
        var words = new LinkedHashSet<String>();
        while (matcher.find() && words.size() < 8) words.add(matcher.group());
        if (words.isEmpty()) throw new IllegalArgumentException("Retrieval query has no search terms");

        var sql = new StringBuilder("""
                SELECT c.id, c.document_id, c.ordinal_no, c.section_text, c.text_sha256,
                       d.symbol, d.accession, d.source_url, d.raw_sha256, d.accepted_at, d.fetched_at
                FROM rag_chunk c JOIN rag_document d ON d.id=c.document_id
                WHERE d.symbol=? AND d.index_status='KEYWORD_READY'
                  AND d.accepted_at>=? AND d.accepted_at<=? AND d.fetched_at<=?
                  AND NOT EXISTS (SELECT 1 FROM rag_document newer
                      WHERE newer.symbol=d.symbol AND newer.accession=d.accession
                        AND newer.document_name=d.document_name AND newer.fetched_at<=?
                        AND (newer.fetched_at>d.fetched_at OR
                             (newer.fetched_at=d.fetched_at AND newer.id>d.id)))
                  AND (
                """);
        var args = new ArrayList<Object>();
        var cutoff = LocalDateTime.ofInstant(asOf, ZoneOffset.UTC);
        args.add(symbol);
        args.add(LocalDateTime.ofInstant(asOf.minusSeconds(lookbackDays * 86400L), ZoneOffset.UTC));
        args.add(cutoff);
        args.add(cutoff);
        args.add(cutoff);
        int index = 0;
        for (String word : words) {
            if (index++ > 0) sql.append(" OR ");
            sql.append("LOWER(c.section_text) LIKE ?");
            args.add("%" + word + "%");
        }
        sql.append(" ) ORDER BY d.accepted_at DESC, d.id DESC, c.ordinal_no LIMIT ?");
        args.add(CANDIDATE_LIMIT);
        var candidates = jdbc.query(sql.toString(), (rs, row) -> new Evidence(
                rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
                rs.getObject(10, LocalDateTime.class).toInstant(ZoneOffset.UTC),
                rs.getObject(11, LocalDateTime.class).toInstant(ZoneOffset.UTC)), args.toArray());
        return candidates.stream()
                .sorted(Comparator.<Evidence>comparingInt(e -> score(e.text(), words)).reversed()
                        .thenComparing(Evidence::acceptedAt, Comparator.reverseOrder())
                        .thenComparing(Evidence::documentId, Comparator.reverseOrder())
                        .thenComparingInt(Evidence::ordinal))
                .limit(topK).toList();
    }

    private static int score(String text, LinkedHashSet<String> words) {
        String lower = text.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String word : words) if (lower.contains(word)) score++;
        return score;
    }

    public record Evidence(long chunkId, long documentId, int ordinal, String text, String textSha256,
                           String symbol, String accession, String sourceUrl, String rawSha256,
                           Instant acceptedAt, Instant fetchedAt) {}
}
