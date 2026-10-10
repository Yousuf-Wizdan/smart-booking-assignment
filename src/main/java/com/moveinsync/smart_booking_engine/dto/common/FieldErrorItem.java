package com.moveinsync.smart_booking_engine.dto.common;

/** One field-level validation error inside {@code ApiError}. */
public record FieldErrorItem(
        String field,
        String message
) {
}
