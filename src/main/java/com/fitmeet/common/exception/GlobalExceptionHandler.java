package com.fitmeet.common.exception;

import com.fitmeet.common.response.ApiResponse;
import com.fitmeet.common.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(RateLimitedException exception) {
        return ResponseEntity.status(429).header("Retry-After", Long.toString(exception.getRetryAfterSeconds()))
                .body(ApiResponse.error(new ErrorResponse(ErrorCode.AUTH_RATE_LIMITED.getCode(),
                        ErrorCode.AUTH_RATE_LIMITED.getMessage())));
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataUnavailable() {
        return ResponseEntity.status(503).body(ApiResponse.error(new ErrorResponse(
                ErrorCode.SERVICE_UNAVAILABLE.getCode(), ErrorCode.SERVICE_UNAVAILABLE.getMessage())));
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Void>> handleBaseException(BaseException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        ErrorResponse error = new ErrorResponse(errorCode.getCode(), errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(ErrorCode.INVALID_INPUT.getMessage());
        ErrorResponse error = new ErrorResponse(ErrorCode.INVALID_INPUT.getCode(), message);
        return ResponseEntity.badRequest().body(ApiResponse.error(error));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        ErrorResponse error = new ErrorResponse(ErrorCode.INVALID_INPUT.getCode(), exception.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(error));
    }
}
