package com.fitmeet.briefing.ingestion;

import java.net.URI;
import java.time.Instant;
import java.util.regex.Pattern;

public record SecFiling(SecIssuer issuer, String accession, String form, Instant acceptedAt, String documentName) {
    private static final Pattern ACCESSION = Pattern.compile("[0-9]{10}-[0-9]{2}-[0-9]{6}");
    private static final Pattern DOCUMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,119}\\.html?", Pattern.CASE_INSENSITIVE);
    public SecFiling {
        if (issuer == null || accession == null || !ACCESSION.matcher(accession).matches()
                || documentName == null || !DOCUMENT.matcher(documentName).matches()
                || !issuer.forms().contains(form)
                || acceptedAt == null) {
            throw new IllegalArgumentException("Invalid SEC filing reference");
        }
    }

    public URI sourceUrl() {
        return URI.create("https://www.sec.gov/Archives/edgar/data/" + issuer.archiveCik() + "/"
                + accession.replace("-", "") + "/" + documentName);
    }
}
