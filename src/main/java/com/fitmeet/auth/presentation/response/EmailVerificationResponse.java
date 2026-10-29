package com.fitmeet.auth.presentation.response;

public record EmailVerificationResponse(String verificationToken, long expiresInSeconds) {}
