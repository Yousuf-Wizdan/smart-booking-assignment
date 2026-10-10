package com.moveinsync.smart_booking_engine.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Capacity is always set. {@code reservedSeats} is an optional admin correction;
 * when present it must stay within {@code 0..capacity}.
 */
public record QuotaUpdateRequest(
        @NotNull @PositiveOrZero Integer capacity,
        @PositiveOrZero Integer reservedSeats
) {
}
