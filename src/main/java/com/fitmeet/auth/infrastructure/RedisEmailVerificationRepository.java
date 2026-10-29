package com.fitmeet.auth.infrastructure;

import com.fitmeet.auth.domain.EmailVerificationCode;
import com.fitmeet.auth.domain.EmailVerificationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RedisEmailVerificationRepository implements EmailVerificationRepository {
    // A new namespace deliberately ignores legacy email-only verified flags.
    private static final DefaultRedisScript<Long> CONFIRM = new DefaultRedisScript<>("""
            local code = redis.call('GET', KEYS[1])
            if not code then return 0 end
            if code ~= ARGV[1] then return -1 end
            redis.call('SET', KEYS[2], ARGV[2], 'EX', 600)
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);
    private final StringRedisTemplate redisTemplate;

    public RedisEmailVerificationRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(String email, EmailVerificationCode code) {
        redisTemplate.opsForValue().set(key(email, "code"), code.value(), code.ttl());
    }

    @Override
    public Confirmation confirm(String email, String code, String proofHash) {
        Long result = redisTemplate.execute(CONFIRM,
                List.of(key(email, "code"), key(email, "proof")), code, proofHash);
        if (Long.valueOf(1).equals(result)) return Confirmation.CONFIRMED;
        if (Long.valueOf(-1).equals(result)) return Confirmation.MISMATCH;
        return Confirmation.NOT_FOUND;
    }

    @Override
    public boolean consumeProof(String email, String proofHash) {
        return Long.valueOf(1).equals(redisTemplate.execute(CONSUME,
                List.of(key(email, "proof")), proofHash));
    }

    private String key(String email, String type) {
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(email.getBytes(StandardCharsets.UTF_8)));
            // Identical hash tags keep both keys on the same Redis Cluster slot.
            return "auth:email-verification:v2:{" + digest + "}:" + type;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
