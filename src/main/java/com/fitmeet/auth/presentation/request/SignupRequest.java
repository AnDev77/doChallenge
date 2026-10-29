package com.fitmeet.auth.presentation.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record SignupRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank @Size(min = 2, max = 30) String nickname,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String verificationToken
) {
}
