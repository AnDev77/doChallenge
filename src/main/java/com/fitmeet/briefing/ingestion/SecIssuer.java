package com.fitmeet.briefing.ingestion;

import java.util.Set;
import java.util.regex.Pattern;

/** The configured SEC identity and eligible form types for one supported ticker. */
public record SecIssuer(String symbol, String cik, Set<String> forms) {
    private static final Pattern SYMBOL = Pattern.compile("[A-Z][A-Z0-9.-]{0,11}");
    private static final Pattern CIK = Pattern.compile("[0-9]{10}");
    private static final Pattern FORM = Pattern.compile("[0-9A-Z-]+(?:/[A-Z])?");

    public SecIssuer {
        if (symbol == null || !SYMBOL.matcher(symbol).matches()
                || cik == null || !CIK.matcher(cik).matches() || Long.parseLong(cik) == 0
                || forms == null || forms.isEmpty() || forms.stream().anyMatch(form ->
                        form == null || !FORM.matcher(form).matches())) {
            throw new IllegalArgumentException("Invalid SEC issuer configuration");
        }
        forms = Set.copyOf(forms);
    }

    public String archiveCik() {
        return Long.toString(Long.parseLong(cik));
    }
}
