package com.fitmeet.briefing.ingestion;

import java.nio.file.Path;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.briefing.ingestion", name = "run-once", havingValue = "true")
public class CamtIngestionRunner implements ApplicationRunner {
    private final FilingIngestionService ingestion;
    private final String userAgent;
    private final Path rawDirectory;
    private final int lookbackDays;
    private final int maxFilings;

    public CamtIngestionRunner(FilingIngestionService ingestion,
            @Value("${app.briefing.ingestion.user-agent:}") String userAgent,
            @Value("${app.briefing.ingestion.raw-directory:./data/sec-filings}") String rawDirectory,
            @Value("${app.briefing.ingestion.lookback-days:30}") int lookbackDays,
            @Value("${app.briefing.ingestion.max-filings:5}") int maxFilings) {
        this.ingestion = ingestion;
        this.userAgent = userAgent;
        this.rawDirectory = Path.of(rawDirectory);
        this.lookbackDays = lookbackDays;
        this.maxFilings = maxFilings;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        int saved = ingestion.collect(userAgent, rawDirectory, Instant.now(), lookbackDays, maxFilings);
        System.out.println("CAMT SEC ingestion completed; new document versions: " + saved);
    }
}
