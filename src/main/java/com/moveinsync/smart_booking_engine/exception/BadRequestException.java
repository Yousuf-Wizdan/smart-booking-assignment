package com.moveinsync.smart_booking_engine.exception;

/** 400 — the request is semantically invalid beyond what Bean Validation covers. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
