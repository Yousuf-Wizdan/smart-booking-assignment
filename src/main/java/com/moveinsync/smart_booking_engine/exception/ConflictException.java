package com.moveinsync.smart_booking_engine.exception;

/** 409 — the request is valid but conflicts with current state (duplicate, quota race, bad transition). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
