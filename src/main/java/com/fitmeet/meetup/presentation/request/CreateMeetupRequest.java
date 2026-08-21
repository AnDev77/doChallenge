package com.fitmeet.meetup.presentation.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateMeetupRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 1000) String description,
        @NotBlank @Size(max = 50) String region,
        @NotBlank @Size(max = 255) String address,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @Min(2) @Max(500) int maxMemberCount
) {
}
