package com.moveinsync.smart_booking_engine.exception;

import com.moveinsync.smart_booking_engine.dto.common.FieldErrorItem;
import java.time.Instant;
import java.util.List;

/** Consistent error body for every 4xx/5xx produced by {@code GlobalExceptionHandler}. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldErrorItem> fieldErrors,
        String requestId
) {
}
