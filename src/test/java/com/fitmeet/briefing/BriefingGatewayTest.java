package com.fitmeet.briefing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BriefingGatewayTest {
    private static final String TOKEN = "test-placeholder-32-characters-minimum";
    private HttpServer upstream;
    private BriefingGateway gateway;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final AtomicInteger redirects = new AtomicInteger();

    @BeforeEach
    void startUpstream() throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/api/v1/briefings/CAMT", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            query.set(exchange.getRequestURI().getRawQuery());
            var headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "application/json; charset=UTF-8");
            headers.set("Location", "/unexpected-redirect");
            respond(exchange, responseStatus.get(), body.get());
        });
        upstream.createContext("/unexpected-redirect", exchange -> {
            redirects.incrementAndGet();
            respond(exchange, 200, "{}");
        });
        upstream.start();
        gateway = new BriefingGateway(
                new ObjectMapper(), "http://127.0.0.1:" + upstream.getAddress().getPort(), TOKEN
        );
    }

    @AfterEach
    void stopUpstream() {
        if (upstream != null) {
            upstream.stop(0);
        }
    }

    @Test
    void upstreamReceivesOnlyInternalAuthAndPublicResponseOmitsInternalMetadata() {
        body.set("""
                {"symbol":"CAMT","status":"ready","stance":"withheld","summary":[],
                 "bullish_factors":[],"bearish_factors":[],"change_conditions":[],
                 "citations":[],"generated_at":"2026-11-15T12:00:00Z","freshness":"current",
                 "source_sha256":"internal"}
                """);
        var result = gateway.lookup("CAMT");
        assertThat(result.path("status").asText()).isEqualTo("ready");
        assertThat(result.has("source_sha256")).isFalse();
        assertThat(authorization.get()).isEqualTo("Bearer " + TOKEN);
        assertThat(query.get()).isEqualTo("window=7d");
    }

    @Test
    void refusesRedirectAndDoesNotReturnUpstreamBody() {
        responseStatus.set(302);
        body.set("{\"status\":\"ready\",\"secret\":\"never forward\"}");
        var result = gateway.lookup("CAMT");
        assertThat(result.path("status").asText()).isEqualTo("failed");
        assertThat(result.size()).isEqualTo(2);
        assertThat(redirects.get()).isZero();
    }

    @Test
    void wrongSymbolAndOversizeResponseFailClosed() {
        body.set("{\"symbol\":\"SPCX\",\"status\":\"insufficient_sources\"}");
        assertThat(gateway.lookup("CAMT").path("status").asText()).isEqualTo("failed");
        body.set(" ".repeat(66_000));
        assertThat(gateway.lookup("CAMT").path("status").asText()).isEqualTo("failed");
        body.set("{\"symbol\":\"CAMT\",\"status\":\"insufficient_sources\"}");
        assertThat(gateway.lookup("CAMT").path("status").asText()).isEqualTo("insufficient_sources");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } finally {
            exchange.close();
        }
    }
}
