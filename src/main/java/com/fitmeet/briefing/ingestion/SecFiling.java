package com.fitmeet.briefing.ingestion;

import java.net.URI;
import java.time.Instant;
import java.util.regex.Pattern;

public record SecFiling(String accession, String form, Instant acceptedAt, String documentName) {
    private static final Pattern ACCESSION = Pattern.compile("[0-9]{10}-[0-9]{2}-[0-9]{6}");
    private static final Pattern DOCUMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,119}\\.html?", Pattern.CASE_INSENSITIVE);
    public static final String CIK = "0001109138";
    public static final String SYMBOL = "CAMT";

    public SecFiling {
        if (accession == null || !ACCESSION.matcher(accession).matches()
                || documentName == null || !DOCUMENT.matcher(documentName).matches()
                || !java.util.Set.of("6-K", "6-K/A", "20-F", "20-F/A").contains(form)
                || acceptedAt == null) {
            throw new IllegalArgumentException("Invalid CAMT SEC filing reference");
        }
    }

    public URI sourceUrl() {
        return URI.create("https://www.sec.gov/Archives/edgar/data/1109138/"
                + accession.replace("-", "") + "/" + documentName);
    }
}
