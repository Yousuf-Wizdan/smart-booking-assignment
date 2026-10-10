package com.moveinsync.smart_booking_engine.dto.request;

import com.moveinsync.smart_booking_engine.enums.BookingDirection;
import com.moveinsync.smart_booking_engine.enums.RoutePosition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A booking evaluation/creation request.
 *
 * <p>{@code employeeId} may only be supplied by an ADMIN acting on behalf of an
 * employee; for any other caller the server uses the employee linked to the
 * authenticated principal and rejects a mismatching {@code employeeId}.
 */
public record BookingRequest(
        UUID employeeId,
        @NotNull LocalDate bookingDate,
        @NotNull BookingDirection direction,
        @NotBlank @Size(max = 120) String pickupLocation,
        @NotBlank @Size(max = 120) String dropLocation,
        @NotNull LocalDateTime requestedTime,
        @NotNull RoutePosition routePosition,
        Boolean hasPackedLunch,
        Boolean requiresParking,
        Boolean escortAvailable
) {
}
