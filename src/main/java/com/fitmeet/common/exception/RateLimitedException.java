package com.fitmeet.common.exception;

public class RateLimitedException extends BaseException {
    private final long retryAfterSeconds;
    public RateLimitedException(long retryAfterSeconds) {
        this(ErrorCode.AUTH_RATE_LIMITED, retryAfterSeconds);
    }
    public RateLimitedException(ErrorCode errorCode, long retryAfterSeconds) {
        super(errorCode);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
