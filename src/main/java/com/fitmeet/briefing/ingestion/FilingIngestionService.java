package com.fitmeet.briefing.ingestion;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class FilingIngestionService {
    private final SecClient sec;
    private final RawFilingStore rawStore;
    private final FilingDocumentRepository documents;

    public FilingIngestionService(SecClient sec, RawFilingStore rawStore, FilingDocumentRepository documents) {
        this.sec = sec;
        this.rawStore = rawStore;
        this.documents = documents;
    }

    public int collect(String userAgent, Path rawDirectory, Instant asOf, int lookbackDays, int maxFilings)
            throws IOException, InterruptedException {
        var filings = SecFilingCatalog.select(sec.submissions(userAgent), asOf, lookbackDays, maxFilings);
        int saved = 0;
        for (SecFiling filing : filings) {
            byte[] raw = sec.document(filing, userAgent);
            var chunks = FilingChunker.chunks(raw);
            var stored = rawStore.save(rawDirectory, raw);
            if (documents.save(filing, Instant.now(), stored, chunks)) saved++;
        }
        return saved;
    }
}
