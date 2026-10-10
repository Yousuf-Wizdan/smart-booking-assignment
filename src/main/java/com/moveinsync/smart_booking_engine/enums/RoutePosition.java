package com.moveinsync.smart_booking_engine.enums;

/**
 * Position of the passenger on the shared route.
 *
 * <p>Modelling assumption (see README): the basic request has no ordered stop list,
 * so the caller states the route position explicitly. The night-safety rule uses
 * {@link #FIRST_PICKUP} and {@link #LAST_DROP}; it never infers position from
 * pickup/drop text.
 */
public enum RoutePosition {
    FIRST_PICKUP,
    LAST_DROP,
    OTHER
}
