package com.fitmeet.auth.domain;

public record AccessToken(
        String value,
        long expiresInSeconds
) {
}
