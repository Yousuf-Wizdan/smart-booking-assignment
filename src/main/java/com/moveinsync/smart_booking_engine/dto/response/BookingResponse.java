package com.moveinsync.smart_booking_engine.dto.response;

import com.moveinsync.smart_booking_engine.entity.Booking;
import com.moveinsync.smart_booking_engine.enums.BookingDirection;
import com.moveinsync.smart_booking_engine.enums.BookingStatus;
import com.moveinsync.smart_booking_engine.enums.Decision;
import com.moveinsync.smart_booking_engine.enums.ReasonCode;
import com.moveinsync.smart_booking_engine.enums.RoutePosition;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID employeeId,
        String employeeCode,
        LocalDate bookingDate,
        BookingDirection direction,
        String pickupLocation,
        String dropLocation,
        LocalDateTime requestedTime,
        RoutePosition routePosition,
        boolean hasPackedLunch,
        boolean requiresParking,
        boolean escortAvailable,
        BookingStatus status,
        Decision decision,
        ReasonCode decidingReason,
        LocalDateTime createdAt
) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getEmployee().getId(),
                booking.getEmployee().getEmployeeCode(),
                booking.getBookingDate(),
                booking.getDirection(),
                booking.getPickupLocation(),
                booking.getDropLocation(),
                booking.getRequestedTime(),
                booking.getRoutePosition(),
                booking.isHasPackedLunch(),
                booking.isRequiresParking(),
                booking.isEscortAvailable(),
                booking.getStatus(),
                booking.getDecision(),
                booking.getDecidingReason(),
                LocalDateTime.ofInstant(booking.getCreatedAt(),
                        java.time.ZoneId.of("UTC")));
    }
}
