package com.moveinsync.smart_booking_engine.exception;

/** 403 — the caller is authenticated but may not perform this action. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
