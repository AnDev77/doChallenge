package com.fitmeet.common.exception;

public class RateLimitedException extends BaseException {
    private final long retryAfterSeconds;
    public RateLimitedException(long retryAfterSeconds) {
        super(ErrorCode.AUTH_RATE_LIMITED);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
