package com.fitmeet.financechat;

import static org.assertj.core.api.Assertions.*;
import com.fitmeet.common.exception.ErrorCode;
import com.fitmeet.common.exception.GlobalExceptionHandler;
import com.fitmeet.common.exception.RateLimitedException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@EnabledIfEnvironmentVariable(named = "REDIS_TEST_HOST", matches = ".+")
class RedisFinancePostLimiterIntegrationTest {
    private static LettuceConnectionFactory connection;
    private static StringRedisTemplate redis;

    @BeforeAll static void connect() {
        connection = new LettuceConnectionFactory(System.getenv("REDIS_TEST_HOST"), 6379);
        connection.afterPropertiesSet();
        redis = new StringRedisTemplate(connection);
        try (var client = connection.getConnection()) {
            assertThat(client.ping()).isEqualTo("PONG");
        }
    }

    @AfterAll static void close() { connection.destroy(); }

    @Test void sharedQuotaCoversBothAppInstancesAndReturnsChatRetryAfter() {
        Long member = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        var blue = new RedisFinancePostLimiter(redis);
        var green = new RedisFinancePostLimiter(redis);
        for (int i = 0; i < 5; i++) blue.check(member);
        for (int i = 0; i < 5; i++) green.check(member);
        assertThatThrownBy(() -> blue.check(member)).isInstanceOfSatisfying(
                RateLimitedException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CHAT_RATE_LIMITED);
                    assertThat(error.getRetryAfterSeconds()).isBetween(1L, 60L);
                    var response = new GlobalExceptionHandler().handleRateLimit(error);
                    assertThat(response.getStatusCode().value()).isEqualTo(429);
                    assertThat(response.getHeaders().getFirst("Retry-After")).isNotBlank();
                    assertThat(response.getBody().data()).isNull();
                });
        green.check(member + 1);
    }
}
