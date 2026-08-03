package com.fitmeet.auth.infrastructure;

import com.fitmeet.auth.domain.AccessToken;
import com.fitmeet.auth.domain.TokenProvider;
import com.fitmeet.member.domain.Member;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider implements TokenProvider {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String TOKEN_HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final String secret;
    private final long accessTokenExpirationSeconds;
    private final Clock clock;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration-seconds}") long accessTokenExpirationSeconds
    ) {
        this.secret = secret;
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
        this.clock = Clock.systemUTC();
    }

    @Override
    public AccessToken createAccessToken(Member member) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plusSeconds(accessTokenExpirationSeconds);

        String payload = """
                {"sub":"%d","email":"%s","role":"%s","iat":%d,"exp":%d}
                """.formatted(
                member.getId(),
                escapeJson(member.getEmail()),
                member.getRole().name(),
                now.getEpochSecond(),
                expiresAt.getEpochSecond()
        ).trim();

        String headerPart = base64Url(TOKEN_HEADER.getBytes(StandardCharsets.UTF_8));
        String payloadPart = base64Url(payload.getBytes(StandardCharsets.UTF_8));
        String signaturePart = sign(headerPart + "." + payloadPart);

        return new AccessToken(headerPart + "." + payloadPart + "." + signaturePart, accessTokenExpirationSeconds);
    }

    private String sign(String content) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return base64Url(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create JWT signature.", exception);
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
