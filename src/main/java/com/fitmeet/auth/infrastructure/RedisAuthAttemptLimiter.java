package com.fitmeet.auth.infrastructure;

import com.fitmeet.auth.domain.AuthAttemptLimiter;
import com.fitmeet.common.exception.RateLimitedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisAuthAttemptLimiter implements AuthAttemptLimiter {
    private static final DefaultRedisScript<Long> CHECK = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
            if count > tonumber(ARGV[1]) then return math.max(1, redis.call('TTL', KEYS[1])) end
            return 0
            """, Long.class);
    private final StringRedisTemplate redis;
    public RedisAuthAttemptLimiter(StringRedisTemplate redis) { this.redis = redis; }
    @Override
    public void check(String action, String identity, int limit, int windowSeconds) {
        if (limit < 1 || windowSeconds < 1) throw new IllegalArgumentException("Invalid rate limit");
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
            Long wait = redis.execute(CHECK, List.of("auth:rate:" + action + ":" + hash),
                    Integer.toString(limit), Integer.toString(windowSeconds));
            if (wait == null) throw new IllegalStateException("Rate limit unavailable");
            if (wait > 0) throw new RateLimitedException(wait);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
