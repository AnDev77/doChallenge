package com.fitmeet.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class EmailVerificationCodeTest {

    @Test
    void sixDigitCodeMatchesSameValue() {
        EmailVerificationCode code = EmailVerificationCode.of("123456", Duration.ofMinutes(5));

        assertThat(code.matches("123456")).isTrue();
    }

    @Test
    void codeDoesNotMatchDifferentValue() {
        EmailVerificationCode code = EmailVerificationCode.of("123456", Duration.ofMinutes(5));

        assertThat(code.matches("654321")).isFalse();
    }

    @Test
    void codeMustBeSixDigits() {
        assertThatThrownBy(() -> EmailVerificationCode.of("12345", Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
