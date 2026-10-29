package com.fitmeet.auth.infrastructure;

import static org.assertj.core.api.Assertions.*;
import com.fitmeet.auth.domain.EmailVerificationCode;
import com.fitmeet.auth.domain.EmailVerificationRepository.Confirmation;
import java.time.Duration;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@EnabledIfEnvironmentVariable(named = "REDIS_TEST_HOST", matches = ".+")
class EmailProofRedisIntegrationTest {
    private static LettuceConnectionFactory connection;
    private static StringRedisTemplate template;
    private RedisEmailVerificationRepository repository;
    private String email;

    @BeforeAll static void connect() {
        connection = new LettuceConnectionFactory(System.getenv("REDIS_TEST_HOST"), 6379);
        connection.afterPropertiesSet();
        template = new StringRedisTemplate(connection);
        try (var client = template.getConnectionFactory().getConnection()) {
            assertThat(client.ping()).isEqualTo("PONG");
        }
    }
    @AfterAll static void close() { connection.destroy(); }
    @BeforeEach void setup() {
        repository = new RedisEmailVerificationRepository(template);
        email = UUID.randomUUID() + "@example.invalid";
    }
    private void seed() { repository.save(email, EmailVerificationCode.of("123456", Duration.ofSeconds(5))); }

    @Test void confirmationAndSignupAreBothOneUse() {
        seed();
        assertThat(repository.confirm(email, "123456", "hash-one")).isEqualTo(Confirmation.CONFIRMED);
        assertThat(repository.confirm(email, "123456", "hash-two")).isEqualTo(Confirmation.NOT_FOUND);
        assertThat(repository.consumeProof(email, "wrong-hash")).isFalse();
        assertThat(repository.consumeProof(email, "hash-one")).isTrue();
        assertThat(repository.consumeProof(email, "hash-one")).isFalse();
    }
    @Test void wrongEmailAndLegacyFlagCannotAuthorizeSignup() {
        seed();
        repository.confirm(email, "123456", "hash-one");
        assertThat(repository.consumeProof("other-" + email, "hash-one")).isFalse();
        String legacy = "auth:email-verification:verified:" + email;
        template.opsForValue().set(legacy, "true", Duration.ofSeconds(5));
        assertThat(repository.consumeProof(email, "true")).isFalse();
        assertThat(repository.consumeProof(email, "hash-one")).isTrue();
    }
    @Test void wrongAndExpiredCodeCannotConfirm() throws Exception {
        seed();
        assertThat(repository.confirm(email, "654321", "hash")).isEqualTo(Confirmation.MISMATCH);
        repository.save(email, EmailVerificationCode.of("123456", Duration.ofMillis(1)));
        Thread.sleep(30);
        assertThat(repository.confirm(email, "123456", "hash")).isEqualTo(Confirmation.NOT_FOUND);
        assertThat(repository.consumeProof(email, "hash")).isFalse();
    }
    @Test void proofHasTenMinuteLifetimeAndExpiredProofFails() throws Exception {
        seed();
        repository.confirm(email, "123456", "hash");
        String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(email.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String key = "auth:email-verification:v2:{" + digest + "}:proof";
        assertThat(template.getExpire(key)).isBetween(590L, 600L);
        template.expire(key, Duration.ZERO);
        assertThat(repository.consumeProof(email, "hash")).isFalse();
    }
    @Test void concurrentSignupsHaveExactlyOneWinner() throws Exception {
        seed();
        repository.confirm(email, "123456", "hash");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Boolean> consume = () -> { start.await(); return repository.consumeProof(email, "hash"); };
            var first = executor.submit(consume);
            var second = executor.submit(consume);
            start.countDown();
            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
    }
    @Test void simultaneousConfirmationsCannotIssueTwoValidProofs() throws Exception {
        seed();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var first = executor.submit(() -> { start.await(); return repository.confirm(email, "123456", "first"); });
            var second = executor.submit(() -> { start.await(); return repository.confirm(email, "123456", "second"); });
            start.countDown();
            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(Confirmation.CONFIRMED, Confirmation.NOT_FOUND);
            int successes = (repository.consumeProof(email, "first") ? 1 : 0)
                    + (repository.consumeProof(email, "second") ? 1 : 0);
            assertThat(successes).isEqualTo(1);
        }
    }
}
