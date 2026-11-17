package com.fitmeet.briefing.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class SecClient {
    private static final int MAX_BYTES = 4_000_000;
    private static final URI SUBMISSIONS = URI.create("https://data.sec.gov/submissions/CIK0001109138.json");
    private final HttpClient client;
    private final ObjectMapper mapper;

    @Autowired
    public SecClient(ObjectMapper mapper) {
        this(mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    SecClient(ObjectMapper mapper, HttpClient client) {
        this.mapper = mapper;
        this.client = client;
    }

    public JsonNode submissions(String userAgent) throws IOException, InterruptedException {
        return mapper.readTree(download(SUBMISSIONS, "application/json", userAgent));
    }

    public byte[] document(SecFiling filing, String userAgent) throws IOException, InterruptedException {
        return download(filing.sourceUrl(), "text/html", userAgent);
    }

    private byte[] download(URI uri, String media, String userAgent) throws IOException, InterruptedException {
        if (userAgent == null || userAgent.isBlank() || userAgent.indexOf('@') < 1
                || userAgent.contains("\r") || userAgent.contains("\n")) {
            throw new IllegalArgumentException("Configure SEC_USER_AGENT with an identifiable contact");
        }
        HttpRequest request = HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(15))
                .header("User-Agent", userAgent).header("Accept", media).build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            String actual = response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].trim();
            boolean expectedMedia = actual.equalsIgnoreCase(media)
                    || (media.equals("text/html") && actual.equalsIgnoreCase("application/xhtml+xml"));
            if (response.statusCode() != 200 || !expectedMedia) {
                throw new IOException("SEC returned an unexpected status or media type");
            }
            if (response.headers().firstValueAsLong("Content-Length").orElse(0) > MAX_BYTES) {
                throw new IOException("SEC response exceeds size limit");
            }
            byte[] data = body.readNBytes(MAX_BYTES + 1);
            if (data.length > MAX_BYTES) throw new IOException("SEC response exceeds size limit");
            return data;
        }
    }
}
