package com.fitmeet.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "Invalid request."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_001", "Member not found."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "MEMBER_002", "Email is already in use."),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "AUTH_001", "Email is already registered."),
    EMAIL_VERIFICATION_CODE_NOT_FOUND(HttpStatus.BAD_REQUEST, "AUTH_002", "Email verification code is expired or not found."),
    EMAIL_VERIFICATION_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "AUTH_003", "Email verification code does not match."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "AUTH_004", "Email verification is required.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
