package com.fitmeet.briefing.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SecFilingCatalog {
    private SecFilingCatalog() {}

    public static List<SecFiling> select(JsonNode submissions, SecIssuer issuer,
            Instant asOf, int lookbackDays, int limit) {
        if (issuer == null || asOf == null || lookbackDays < 1 || lookbackDays > 365 || limit < 1 || limit > 20) {
            throw new IllegalArgumentException("Invalid filing selection bounds");
        }
        if (submissions.path("cik").asLong(-1) != Long.parseLong(issuer.cik())
                || !contains(submissions.path("tickers"), issuer.symbol())) {
            throw new IllegalArgumentException("SEC issuer does not match configured CIK and symbol");
        }
        JsonNode recent = submissions.path("filings").path("recent");
        JsonNode accessions = recent.path("accessionNumber");
        JsonNode forms = recent.path("form");
        JsonNode accepted = recent.path("acceptanceDateTime");
        JsonNode documents = recent.path("primaryDocument");
        int count = accessions.size();
        if (!accessions.isArray() || !forms.isArray() || !accepted.isArray() || !documents.isArray()
                || count != forms.size() || count != accepted.size() || count != documents.size()) {
            throw new IllegalArgumentException("Incomplete SEC submissions columns");
        }
        Instant cutoff = asOf.minus(Duration.ofDays(lookbackDays));
        List<SecFiling> selected = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String form = forms.get(i).asText();
            if (!issuer.forms().contains(form)) {
                continue;
            }
            Instant when = Instant.parse(accepted.get(i).asText());
            if (when.isAfter(asOf) || when.isBefore(cutoff)) {
                continue;
            }
            selected.add(new SecFiling(issuer, accessions.get(i).asText(), form, when, documents.get(i).asText()));
        }
        selected.sort(Comparator.comparing(SecFiling::acceptedAt).thenComparing(SecFiling::accession).reversed());
        return List.copyOf(selected.subList(0, Math.min(limit, selected.size())));
    }

    private static boolean contains(JsonNode values, String target) {
        if (!values.isArray()) return false;
        for (JsonNode value : values) if (target.equals(value.asText())) return true;
        return false;
    }
}
