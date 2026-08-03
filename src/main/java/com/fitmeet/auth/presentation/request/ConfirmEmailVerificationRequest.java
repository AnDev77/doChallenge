package com.fitmeet.auth.presentation.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmEmailVerificationRequest(
        @Email @NotBlank String email,
        @Pattern(regexp = "\\d{6}", message = "Email verification code must be 6 digits.")
        String code
) {
}
