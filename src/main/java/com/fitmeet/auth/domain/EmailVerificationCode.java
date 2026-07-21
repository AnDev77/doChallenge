package com.fitmeet.auth.domain;

import java.time.Duration;

public record EmailVerificationCode(
        String value,
        Duration ttl
) {

    public static EmailVerificationCode of(String value, Duration ttl) {
        if (value == null || !value.matches("\\d{6}")) {
            throw new IllegalArgumentException("Email verification code must be 6 digits.");
        }
        return new EmailVerificationCode(value, ttl);
    }

    public boolean matches(String candidate) {
        return value.equals(candidate);
    }
}
