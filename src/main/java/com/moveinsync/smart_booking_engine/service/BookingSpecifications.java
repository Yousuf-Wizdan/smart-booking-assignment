package com.moveinsync.smart_booking_engine.service;

import com.moveinsync.smart_booking_engine.entity.Booking;
import com.moveinsync.smart_booking_engine.enums.BookingStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Optional filters for the booking search endpoint; all are nullable. */
final class BookingSpecifications {

    private BookingSpecifications() {
    }

    static Specification<Booking> employeeId(UUID employeeId) {
        return (root, query, criteria) -> employeeId == null ? null
                : criteria.equal(root.get("employee").get("id"), employeeId);
    }

    static Specification<Booking> date(LocalDate date) {
        return (root, query, criteria) -> date == null ? null
                : criteria.equal(root.get("bookingDate"), date);
    }

    static Specification<Booking> status(BookingStatus status) {
        return (root, query, criteria) -> status == null ? null
                : criteria.equal(root.get("status"), status);
    }
}
