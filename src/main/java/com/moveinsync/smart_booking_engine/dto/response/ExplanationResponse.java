package com.moveinsync.smart_booking_engine.dto.response;

import com.moveinsync.smart_booking_engine.enums.Decision;
import com.moveinsync.smart_booking_engine.enums.ReasonCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The stored decision snapshot for a booking. It explains the original evaluation
 * even after policy rules change later.
 */
public record ExplanationResponse(
        UUID bookingId,
        Decision decision,
        ReasonCode decidingReason,
        List<ReasonCode> failureReasons,
        List<RuleResultResponse> ruleResults,
        Instant snapshotAt
) {
}
