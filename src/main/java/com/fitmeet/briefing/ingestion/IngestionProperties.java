package com.fitmeet.briefing.ingestion;

import java.util.HashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.briefing.ingestion")
public class IngestionProperties {
    private String userAgent = "";
    private String rawDirectory = "./data/sec-filings";
    private int lookbackDays = 30;
    private int maxFilings = 5;
    private List<SecIssuer> issuers = List.of();

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getRawDirectory() { return rawDirectory; }
    public void setRawDirectory(String rawDirectory) { this.rawDirectory = rawDirectory; }
    public int getLookbackDays() { return lookbackDays; }
    public void setLookbackDays(int lookbackDays) { this.lookbackDays = lookbackDays; }
    public int getMaxFilings() { return maxFilings; }
    public void setMaxFilings(int maxFilings) { this.maxFilings = maxFilings; }
    public List<SecIssuer> getIssuers() { return issuers; }
    public void setIssuers(List<SecIssuer> issuers) { this.issuers = List.copyOf(issuers); }

    public List<SecIssuer> validatedIssuers() {
        if (issuers.isEmpty()) throw new IllegalArgumentException("Configure at least one SEC issuer");
        var symbols = new HashSet<String>();
        for (SecIssuer issuer : issuers) {
            if (!symbols.add(issuer.symbol())) throw new IllegalArgumentException("Duplicate SEC symbol");
        }
        return issuers;
    }
}
