package com.fitmeet.financechat;

import com.fitmeet.common.exception.ErrorCode;
import com.fitmeet.common.exception.BaseException;
import com.fitmeet.common.exception.RateLimitedException;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisFinancePostLimiter implements FinancePostLimiter {
    // One atomic counter across both rooms and both Blue/Green app versions.
    private static final DefaultRedisScript<Long> CHECK = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], 60) end
            if count > 10 then return math.max(1, redis.call('TTL', KEYS[1])) end
            return 0
            """, Long.class);
    private final StringRedisTemplate redis;

    public RedisFinancePostLimiter(StringRedisTemplate redis) { this.redis = redis; }

    @Override
    public void check(Long memberId) {
        Long wait = redis.execute(CHECK, List.of("finance:chat:post:v1:" + memberId));
        if (wait == null) throw new BaseException(ErrorCode.SERVICE_UNAVAILABLE);
        if (wait > 0) throw new RateLimitedException(ErrorCode.CHAT_RATE_LIMITED, wait);
    }
}
