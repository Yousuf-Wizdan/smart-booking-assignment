package com.moveinsync.smart_booking_engine.dto.response;

import com.moveinsync.smart_booking_engine.enums.Decision;
import com.moveinsync.smart_booking_engine.enums.ReasonCode;
import java.time.Instant;
import java.util.List;

/** The advisory evaluation result; evaluating never mutates quota or bookings. */
public record EvaluationResponse(
        Decision decision,
        ReasonCode decidingReason,
        List<ReasonCode> failureReasons,
        List<RuleResultResponse> ruleResults,
        Instant evaluatedAt,
        String requestId
) {
}
