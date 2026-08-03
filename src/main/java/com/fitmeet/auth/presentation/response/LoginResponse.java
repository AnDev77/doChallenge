package com.fitmeet.auth.presentation.response;

public record LoginResponse(
        String tokenType,
        String accessToken,
        long expiresInSeconds
) {
}
