package com.fitmeet.auth.domain;

public interface AuthAttemptLimiter {
    void check(String action, String identity, int limit, int windowSeconds);
}
