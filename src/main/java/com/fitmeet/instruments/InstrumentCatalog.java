package com.fitmeet.instruments;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InstrumentCatalog {
    public record Instrument(
            String symbol, String name, String chartSymbol, String investorUrl,
            boolean featured, boolean watchlist, boolean briefing, boolean discussion
    ) {}

    // First release is deliberately small. Keep this API stable when the list moves to a database.
    private final List<Instrument> instruments;
    private final Map<String, Instrument> bySymbol;

    public InstrumentCatalog(@Value("${app.briefing.public-provider:python}") String provider) {
        // Only CAMT has a Java SEC retrieval path. The public GET still requires an approved draft.
        instruments = List.of(
            new Instrument("CAMT", "캠텍 (Camtek)", "NASDAQ:CAMT", "https://www.camtek.com/investors/", true, true, "java".equals(provider), false),
            new Instrument("SPCX", "스페이스X (SpaceX)", "NASDAQ:SPCX", "https://ir.spacex.com/", true, true, false, false),
            new Instrument("AVGO", "브로드컴 (Broadcom)", "NASDAQ:AVGO", "https://investors.broadcom.com/", true, true, false, false),
            new Instrument("QQQ", "나스닥 100 ETF", "NASDAQ:QQQ", null, false, true, false, true),
            new Instrument("SPY", "S&P 500 ETF", "AMEX:SPY", null, false, true, false, true)
        );
        bySymbol = instruments.stream()
                .collect(Collectors.toUnmodifiableMap(Instrument::symbol, Function.identity()));
    }

    public List<Instrument> list() { return instruments; }

    public boolean supportsWatchlist(String symbol) {
        Instrument instrument = bySymbol.get(symbol);
        return instrument != null && instrument.watchlist();
    }
}
