package com.fitmeet.auth.infrastructure;

import com.fitmeet.auth.domain.AccessToken;
import com.fitmeet.auth.domain.AuthenticatedMember;
import com.fitmeet.auth.domain.TokenProvider;
import com.fitmeet.common.exception.BaseException;
import com.fitmeet.common.exception.ErrorCode;
import com.fitmeet.member.domain.Member;
import com.fitmeet.member.domain.MemberRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
    private final ObjectMapper objectMapper;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration-seconds}") long accessTokenExpirationSeconds,
            ObjectMapper objectMapper
    ) {
        this.secret = secret;
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
        this.clock = Clock.systemUTC();
        this.objectMapper = objectMapper;
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

    @Override
    public AuthenticatedMember parseAccessToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new BaseException(ErrorCode.INVALID_ACCESS_TOKEN);
            }

            String unsignedToken = parts[0] + "." + parts[1];
            String expectedSignature = sign(unsignedToken);
            if (!MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8)
            )) {
                throw new BaseException(ErrorCode.INVALID_ACCESS_TOKEN);
            }

            JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            long expiresAt = payload.path("exp").asLong();
            if (Instant.now(clock).getEpochSecond() >= expiresAt) {
                throw new BaseException(ErrorCode.INVALID_ACCESS_TOKEN);
            }

            return new AuthenticatedMember(
                    payload.path("sub").asLong(),
                    payload.path("email").asText(),
                    MemberRole.valueOf(payload.path("role").asText())
            );
        } catch (BaseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BaseException(ErrorCode.INVALID_ACCESS_TOKEN);
        }
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
