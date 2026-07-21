package com.fitmeet.common.response;

public record ErrorResponse(
        String code,
        String message
) {
}
