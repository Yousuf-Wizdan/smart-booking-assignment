package com.moveinsync.smart_booking_engine.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record PolicyPriorityRequest(
        @NotNull @PositiveOrZero Integer priority
) {
}
