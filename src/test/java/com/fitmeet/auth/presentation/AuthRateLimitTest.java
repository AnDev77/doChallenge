package com.fitmeet.auth.presentation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fitmeet.auth.domain.AuthAttemptLimiter;
import com.fitmeet.common.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitTest {
    @Test void doesNotTrustCallerSuppliedForwardedIp() {
        var limiter = mock(AuthAttemptLimiter.class);
        var config = new AuthRateLimitConfig(limiter);
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/email-verifications/send");
        request.setRemoteAddr("192.0.2.1");
        request.addHeader("X-Forwarded-For", "198.51.100.2");
        assertThat(config.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verify(limiter).check("ip-/api/v1/auth/email-verifications/send", "192.0.2.1", 10, 600);
    }
    @Test void retryAfterIsIncludedWithoutLeakingIdentity() {
        var response = new GlobalExceptionHandler().handleRateLimit(new RateLimitedException(42));
        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("42");
    }
    @Test void redisFailureReturnsUnavailableRatherThanFalseSuccess() {
        var response = new GlobalExceptionHandler().handleDataUnavailable();
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().success()).isFalse();
    }
}
