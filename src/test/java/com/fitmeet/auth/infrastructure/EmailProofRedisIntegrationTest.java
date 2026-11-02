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
    @Test void fifthWrongCodeRevokesChallengeAndNewCodeResetsAttempts() {
        seed();
        for (int i = 0; i < 4; i++) {
            assertThat(repository.confirm(email, "000000", "hash")).isEqualTo(Confirmation.MISMATCH);
        }
        assertThat(repository.confirm(email, "000000", "hash")).isEqualTo(Confirmation.ATTEMPTS_EXCEEDED);
        assertThat(repository.confirm(email, "123456", "hash")).isEqualTo(Confirmation.NOT_FOUND);
        seed();
        assertThat(repository.confirm(email, "123456", "hash")).isEqualTo(Confirmation.CONFIRMED);
    }
    @Test void resendInvalidatesProofAndStaleMailFailureCannotDeleteNewCode() {
        seed();
        repository.confirm(email, "123456", "old-proof");
        repository.save(email, EmailVerificationCode.of("654321", Duration.ofSeconds(5)));
        repository.discardCode(email, "123456");
        assertThat(repository.consumeProof(email, "old-proof")).isFalse();
        assertThat(repository.confirm(email, "654321", "new-proof")).isEqualTo(Confirmation.CONFIRMED);
        repository.consumeProof(email, "new-proof");
    }
    @Test void rateLimitIsAtomicAndReturnsRetryDelay() throws Exception {
        var limiter = new RedisAuthAttemptLimiter(template);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Boolean> attempt = () -> {
                start.await();
                try { limiter.check("test", email, 1, 60); return true; }
                catch (com.fitmeet.common.exception.RateLimitedException exception) {
                    assertThat(exception.getRetryAfterSeconds()).isBetween(1L, 60L);
                    return false;
                }
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            start.countDown();
            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
    }
    @Test void independentIdentitiesHaveIndependentLimits() {
        var limiter = new RedisAuthAttemptLimiter(template);
        limiter.check("hour", email, 2, 3600);
        limiter.check("hour", email, 2, 3600);
        assertThatThrownBy(() -> limiter.check("hour", email, 2, 3600))
                .isInstanceOf(com.fitmeet.common.exception.RateLimitedException.class);
        limiter.check("hour", "other-" + email, 2, 3600);
    }

    @Test void mvcSendConfirmSignupLoginUsesRealRedisAndSameProof() throws Exception {
        var members = org.mockito.Mockito.mock(com.fitmeet.member.domain.MemberRepository.class);
        var sender = org.mockito.Mockito.mock(com.fitmeet.auth.domain.EmailVerificationSender.class);
        var generator = org.mockito.Mockito.mock(com.fitmeet.auth.domain.EmailVerificationCodeGenerator.class);
        org.mockito.Mockito.when(generator.generate()).thenReturn(EmailVerificationCode.of("123456", Duration.ofMinutes(5)));
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        var provider = org.mockito.Mockito.mock(com.fitmeet.auth.domain.TokenProvider.class);
        org.mockito.Mockito.when(provider.createAccessToken(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.fitmeet.auth.domain.AccessToken("synthetic-token", 3600));
        org.mockito.Mockito.when(members.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            com.fitmeet.member.domain.Member member = invocation.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(member, "id", 1L);
            org.mockito.Mockito.when(members.findByEmail(email)).thenReturn(java.util.Optional.of(member));
            return member;
        });
        var limiter = new RedisAuthAttemptLimiter(template);
        var verification = new com.fitmeet.auth.application.EmailVerificationService(members, repository, generator, sender, limiter);
        var controller = new com.fitmeet.auth.presentation.AuthController(
                new com.fitmeet.member.application.MemberService(members, encoder), verification,
                new com.fitmeet.auth.application.AuthService(members, encoder, provider));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.fitmeet.common.exception.GlobalExceptionHandler())
                .addInterceptors(new com.fitmeet.auth.presentation.AuthRateLimitConfig(limiter)).build();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String root = "/api/v1/auth";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(root + "/email-verifications/send")
                .contentType("application/json").content(mapper.writeValueAsString(java.util.Map.of("email", email))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(root + "/email-verifications/send")
                .contentType("application/json").content(mapper.writeValueAsString(java.util.Map.of("email", email))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isTooManyRequests());
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(root + "/email-verifications/confirm")
                .contentType("application/json").content(mapper.writeValueAsString(java.util.Map.of("email", email, "code", "123456"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        String proof = mapper.readTree(response.getResponse().getContentAsString()).path("data").path("verificationToken").asText();
        String signup = mapper.writeValueAsString(java.util.Map.of("email", email, "password", "password123", "nickname", "tester", "verificationToken", proof));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(root + "/signup")
                .contentType("application/json").content(signup))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(root + "/signup")
                .contentType("application/json").content(signup))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(root + "/login")
                .contentType("application/json").content(mapper.writeValueAsString(java.util.Map.of("email", email, "password", "password123"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        org.mockito.Mockito.verify(sender, org.mockito.Mockito.times(1)).send(email, "123456");
    }

}
