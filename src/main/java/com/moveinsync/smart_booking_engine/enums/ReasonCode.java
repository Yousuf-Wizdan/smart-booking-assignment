package com.moveinsync.smart_booking_engine.enums;

/**
 * Stable, machine-readable reason codes. These are part of the API contract:
 * clients and tests rely on the exact names.
 */
public enum ReasonCode {
    /** Requested service time is outside the employee's shift window. */
    OUTSIDE_SHIFT_WINDOW,
    /** Booking attempted later than the configured cut-off before service time. */
    PAST_CUTOFF,
    /** A non-cancelled booking already exists for the same employee/service slot. */
    DUPLICATE,
    /** Cost centre has no free seat left (or reservation race lost). */
    QUOTA_EXCEEDED,
    /** No quota row is configured for the employee's cost centre. */
    QUOTA_NOT_CONFIGURED,
    /** Woman employee would be first pickup / last drop at night without an escort. */
    NIGHT_SAFETY,
    /** A policy rule row has missing/invalid parameters; the rule fails closed. */
    INVALID_RULE_PARAMETERS
}
