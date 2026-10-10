package com.moveinsync.smart_booking_engine.exception;

import com.moveinsync.smart_booking_engine.dto.response.EvaluationResponse;

/**
 * Thrown when a booking creation or approval cannot proceed because the rule
 * evaluation denied it (or approval is still required). Carries the full
 * evaluation so the client gets decision + reason codes, not just a message.
 * Mapped to HTTP 409.
 */
public class EvaluationRejectedException extends RuntimeException {

    private final EvaluationResponse evaluation;

    public EvaluationRejectedException(String message, EvaluationResponse evaluation) {
        super(message);
        this.evaluation = evaluation;
    }

    public EvaluationResponse getEvaluation() {
        return evaluation;
    }
}
