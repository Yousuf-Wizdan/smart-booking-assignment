package com.moveinsync.smart_booking_engine.exception;

/** 404 — the requested resource does not exist (or is outside the caller's scope). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
