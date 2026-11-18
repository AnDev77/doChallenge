package com.fitmeet.briefing.retrieval;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Makes a complete, verified set of immutable chunks eligible for keyword search. */
@Service
public class KeywordReadinessService {
    private final JdbcTemplate jdbc;

    public KeywordReadinessService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public int markPendingReady(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid indexing batch size");
        var ids = jdbc.queryForList("SELECT id FROM rag_document WHERE index_status='PENDING' ORDER BY id LIMIT ?",
                Long.class, limit);
        for (long documentId : ids) {
            var chunks = jdbc.query("""
                    SELECT ordinal_no, section_text, text_sha256 FROM rag_chunk
                    WHERE document_id=? ORDER BY ordinal_no
                    """, (rs, row) -> new Chunk(rs.getInt(1), rs.getString(2), rs.getString(3)), documentId);
            if (chunks.isEmpty() || chunks.size() > 1000) throw new IllegalStateException("Invalid chunk count for " + documentId);
            for (int ordinal = 0; ordinal < chunks.size(); ordinal++) {
                var chunk = chunks.get(ordinal);
                if (chunk.ordinal() != ordinal || chunk.text() == null || chunk.text().isBlank()
                        || chunk.text().length() > 1500 || !sha256(chunk.text()).equals(chunk.sha256())) {
                    throw new IllegalStateException("Invalid chunk for " + documentId);
                }
            }
            if (jdbc.update("UPDATE rag_document SET index_status='KEYWORD_READY' WHERE id=? AND index_status='PENDING'",
                    documentId) != 1) throw new IllegalStateException("Document readiness changed for " + documentId);
        }
        return ids.size();
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private record Chunk(int ordinal, String text, String sha256) {}
}
