package com.fitmeet.briefing.ingestion;

import java.nio.file.Path;
import java.time.Instant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.briefing.ingestion", name = "run-once", havingValue = "true")
public class SecIngestionRunner implements ApplicationRunner {
    private final FilingIngestionService ingestion;
    private final IngestionProperties properties;

    public SecIngestionRunner(FilingIngestionService ingestion, IngestionProperties properties) {
        this.ingestion = ingestion;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        var issuers = properties.validatedIssuers();
        int saved = ingestion.collect(issuers, properties.getUserAgent(),
                Path.of(properties.getRawDirectory()), Instant.now(),
                properties.getLookbackDays(), properties.getMaxFilings());
        System.out.println("SEC ingestion completed; new document versions: " + saved);
    }
}
