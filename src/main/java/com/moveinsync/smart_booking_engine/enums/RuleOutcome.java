package com.moveinsync.smart_booking_engine.enums;

/** Result of evaluating one rule within the rule stack. */
public enum RuleOutcome {
    PASS,
    FAIL,
    /** The rule does not apply to this request (e.g. night rule for a non-woman employee). */
    SKIPPED
}
