package com.moveinsync.smart_booking_engine.enums;

/**
 * Lifecycle of a booking.
 *
 * <p>Only {@link #CONFIRMED} and {@link #PENDING_APPROVAL} count as "active" bookings
 * (they block duplicates). {@link #CANCELLED} and {@link #REJECTED} are terminal and
 * free any reserved seat exactly once.
 */
public enum BookingStatus {
    CONFIRMED,
    PENDING_APPROVAL,
    CANCELLED,
    REJECTED
}
