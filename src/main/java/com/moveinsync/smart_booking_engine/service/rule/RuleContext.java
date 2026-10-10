package com.moveinsync.smart_booking_engine.service.rule;

import com.moveinsync.smart_booking_engine.entity.Booking;
import com.moveinsync.smart_booking_engine.entity.Employee;
import com.moveinsync.smart_booking_engine.enums.BookingDirection;
import com.moveinsync.smart_booking_engine.enums.RoutePosition;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Everything a rule needs to judge one booking request. All times are wall-clock
 * values in the configured company timezone; {@code now} is injected (from a
 * {@code Clock}) so tests are deterministic.
 *
 * @param existingBookingId when re-evaluating an existing booking (approval flow),
 *                          its own id, so the duplicate rule ignores it; null otherwise
 */
public record RuleContext(
        Employee employee,
        LocalDateTime bookingDateAndTime,
        BookingDirection direction,
        RoutePosition routePosition,
        boolean escortAvailable,
        LocalDateTime now,
        UUID existingBookingId
) {

    public static RuleContext forRequest(Employee employee, LocalDateTime requestedTime,
                                         BookingDirection direction, RoutePosition routePosition,
                                         boolean escortAvailable, LocalDateTime now) {
        return new RuleContext(employee, requestedTime, direction, routePosition,
                escortAvailable, now, null);
    }

    public static RuleContext forBooking(Booking booking, LocalDateTime now) {
        return new RuleContext(booking.getEmployee(), booking.getRequestedTime(),
                booking.getDirection(), booking.getRoutePosition(),
                booking.isEscortAvailable(), now, booking.getId());
    }
}
