package com.moveinsync.smart_booking_engine.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record QuotaCreateRequest(
        @NotBlank @Size(max = 32) String costCentreCode,
        @NotNull @PositiveOrZero Integer capacity
) {
}
