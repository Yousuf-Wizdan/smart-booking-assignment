package com.moveinsync.smart_booking_engine.dto.request;

import jakarta.validation.constraints.NotNull;

public record PolicyEnabledRequest(
        @NotNull Boolean enabled
) {
}
