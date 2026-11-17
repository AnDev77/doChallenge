package com.fitmeet.briefing.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SecFilingCatalog {
    private SecFilingCatalog() {}

    public static List<SecFiling> select(JsonNode submissions, Instant asOf, int lookbackDays, int limit) {
        if (asOf == null || lookbackDays < 1 || lookbackDays > 365 || limit < 1 || limit > 20) {
            throw new IllegalArgumentException("Invalid filing selection bounds");
        }
        if (submissions.path("cik").asInt(-1) != 1109138
                || !contains(submissions.path("tickers"), SecFiling.SYMBOL)) {
            throw new IllegalArgumentException("SEC issuer does not match CAMT");
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
            if (!java.util.Set.of("6-K", "6-K/A", "20-F", "20-F/A").contains(form)) {
                continue;
            }
            Instant when = Instant.parse(accepted.get(i).asText());
            if (when.isAfter(asOf) || when.isBefore(cutoff)) {
                continue;
            }
            selected.add(new SecFiling(accessions.get(i).asText(), form, when, documents.get(i).asText()));
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
