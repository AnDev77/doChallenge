package com.fitmeet.auth.infrastructure;

import com.fitmeet.auth.domain.EmailVerificationCode;
import com.fitmeet.auth.domain.EmailVerificationRepository;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisEmailVerificationRepository implements EmailVerificationRepository {

    private static final String CODE_KEY_PREFIX = "auth:email-verification:code:";
    private static final String VERIFIED_KEY_PREFIX = "auth:email-verification:verified:";
    private static final Duration READ_TTL = Duration.ofMinutes(5);
    private static final Duration VERIFIED_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;

    public RedisEmailVerificationRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(String email, EmailVerificationCode code) {
        redisTemplate.opsForValue().set(codeKey(email), code.value(), code.ttl());
    }

    @Override
    public Optional<EmailVerificationCode> findByEmail(String email) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(codeKey(email)))
                .map(value -> EmailVerificationCode.of(value, READ_TTL));
    }

    @Override
    public void deleteCode(String email) {
        redisTemplate.delete(codeKey(email));
    }

    @Override
    public void markVerified(String email) {
        redisTemplate.opsForValue().set(verifiedKey(email), "true", VERIFIED_TTL);
    }

    @Override
    public boolean isVerified(String email) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(verifiedKey(email)));
    }

    @Override
    public void deleteVerified(String email) {
        redisTemplate.delete(verifiedKey(email));
    }

    private String codeKey(String email) {
        return CODE_KEY_PREFIX + email;
    }

    private String verifiedKey(String email) {
        return VERIFIED_KEY_PREFIX + email;
    }
}
