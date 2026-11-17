package com.fitmeet.briefing.ingestion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class FilingDocumentRepository {
    private final JdbcTemplate jdbc;

    public FilingDocumentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public boolean save(SecFiling filing, Instant fetchedAt, RawFilingStore.StoredRaw raw, List<String> chunks) {
        String exists = "SELECT COUNT(*) FROM rag_document WHERE symbol=? AND accession=? AND document_name=? AND raw_sha256=?";
        if (jdbc.queryForObject(exists, Integer.class, SecFiling.SYMBOL, filing.accession(),
                filing.documentName(), raw.sha256()) != 0) return false;
        var keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                var statement = connection.prepareStatement("""
                        INSERT INTO rag_document (symbol, issuer_cik, accession, form_type, document_name,
                        source_url, accepted_at, fetched_at, raw_sha256, raw_storage_key, parser_version,
                        chunk_version, index_status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """, java.sql.Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, SecFiling.SYMBOL);
                statement.setString(2, SecFiling.CIK);
                statement.setString(3, filing.accession());
                statement.setString(4, filing.form());
                statement.setString(5, filing.documentName());
                statement.setString(6, filing.sourceUrl().toString());
                statement.setObject(7, LocalDateTime.ofInstant(filing.acceptedAt(), ZoneOffset.UTC));
                statement.setObject(8, LocalDateTime.ofInstant(fetchedAt, ZoneOffset.UTC));
                statement.setString(9, raw.sha256());
                statement.setString(10, raw.key());
                statement.setString(11, FilingChunker.PARSER_VERSION);
                statement.setString(12, FilingChunker.CHUNK_VERSION);
                statement.setString(13, "PENDING");
                return statement;
            }, keys);
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
        long documentId = keys.getKey().longValue();
        for (int i = 0; i < chunks.size(); i++) {
            String text = chunks.get(i);
            jdbc.update("INSERT INTO rag_chunk (document_id, ordinal_no, section_text, text_sha256) VALUES (?,?,?,?)",
                    documentId, i, text, sha256(text));
        }
        return true;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
