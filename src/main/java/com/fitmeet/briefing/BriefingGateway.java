package com.fitmeet.briefing;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BriefingGateway {
    static final Set<String> SYMBOLS = Set.of("CAMT", "SPCX", "QQQ", "SPY");
    private static final Set<String> STATES = Set.of("pending", "insufficient_sources", "failed", "ready");
    private static final List<String> RESULT_FIELDS = List.of(
            "stance", "summary", "bullish_factors", "bearish_factors", "change_conditions",
            "citations", "generated_at", "freshness"
    );
    private static final int LIMIT_BYTES = 65_536;
    private final Semaphore slots = new Semaphore(8);
    private final URI origin;
    private final String token;
    private final ObjectMapper mapper;

    public BriefingGateway(
            ObjectMapper mapper,
            @Value("${app.briefing.upstream-origin:}") String configuredOrigin,
            @Value("${app.briefing.internal-token:}") String token
    ) {
        this.mapper = mapper;
        this.token = token;
        if (configuredOrigin.isBlank() && token.isBlank()) {
            origin = null;
            return;
        }
        URI candidate = URI.create(configuredOrigin);
        if (!("http".equals(candidate.getScheme()) || "https".equals(candidate.getScheme()))
                || candidate.getHost() == null || candidate.getUserInfo() != null
                || candidate.getQuery() != null || candidate.getFragment() != null
                || !Set.of("", "/").contains(candidate.getPath())
                || token.length() < 32 || !token.chars().allMatch(c -> c >= 33 && c <= 126)) {
            throw new IllegalArgumentException("Invalid internal briefing service configuration");
        }
        origin = candidate;
    }

    public ObjectNode lookup(String symbol) {
        if (!SYMBOLS.contains(symbol)) {
            throw new IllegalArgumentException("Unsupported briefing symbol");
        }
        if (origin == null) {
            return state(symbol, "pending");
        }
        if (!slots.tryAcquire()) {
            return state(symbol, "failed");
        }

        HttpURLConnection connection = null;
        try {
            URI request = origin.resolve("/api/v1/briefings/" + symbol + "?window=7d");
            connection = (HttpURLConnection) request.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1200);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            if (connection.getResponseCode() != 200 || !isJson(connection.getContentType())) {
                return state(symbol, "failed");
            }
            try (var stream = connection.getInputStream()) {
                if (connection.getContentLengthLong() > LIMIT_BYTES) {
                    return state(symbol, "failed");
                }
                byte[] body = stream.readNBytes(LIMIT_BYTES + 1);
                if (body.length > LIMIT_BYTES) {
                    return state(symbol, "failed");
                }
                return publicResult(symbol, mapper.reader()
                        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .readTree(new ByteArrayInputStream(body)));
            }
        } catch (IOException error) {
            return state(symbol, "failed");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            slots.release();
        }
    }

    private static boolean isJson(String type) {
        return type != null && type.split(";", 2)[0].trim().equalsIgnoreCase("application/json");
    }

    private ObjectNode publicResult(String symbol, JsonNode body) {
        if (body == null || !body.isObject() || !symbol.equals(body.path("symbol").asText())) {
            return state(symbol, "failed");
        }
        String status = body.path("status").asText();
        if (!STATES.contains(status)) {
            return state(symbol, "failed");
        }
        if (!status.equals("ready")) {
            return state(symbol, status);
        }
        ObjectNode result = state(symbol, status);
        for (String field : RESULT_FIELDS) {
            JsonNode value = body.get(field);
            if (value == null || value.isNull()) {
                return state(symbol, "failed");
            }
            result.set(field, value);
        }
        return result;
    }

    private ObjectNode state(String symbol, String state) {
        return mapper.createObjectNode().put("symbol", symbol).put("status", state);
    }
}
