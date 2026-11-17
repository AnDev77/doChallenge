package com.fitmeet.briefing.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class FilingDocumentRepositoryTest {
    @TempDir Path directory;

    @Test
    void idempotentRevisionKeepsRawAndChunksOfBothVersions() throws Exception {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:filing-versions;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V3__rag_source_documents.sql"))
                .execute(dataSource);
        var jdbc = new JdbcTemplate(dataSource);
        var repository = new FilingDocumentRepository(jdbc);
        var store = new RawFilingStore();
        var filing = new SecFiling("0001109138-26-000001", "6-K", Instant.parse("2026-11-10T12:00:00Z"), "report.htm");
        var first = store.save(directory, "first filing content".getBytes(StandardCharsets.UTF_8));
        var revised = store.save(directory, "corrected filing content".getBytes(StandardCharsets.UTF_8));
        assertThat(repository.save(filing, Instant.now(), first, List.of("first filing content"))).isTrue();
        assertThat(repository.save(filing, Instant.now(), first, List.of("first filing content"))).isFalse();
        assertThat(repository.save(filing, Instant.now(), revised, List.of("corrected filing content"))).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rag_document", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rag_chunk", Integer.class)).isEqualTo(2);
        assertThat(java.nio.file.Files.readString(directory.resolve(first.key()))).isEqualTo("first filing content");
        assertThat(java.nio.file.Files.readString(directory.resolve(revised.key()))).isEqualTo("corrected filing content");
    }
}
