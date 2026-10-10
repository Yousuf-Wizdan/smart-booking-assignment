package com.moveinsync.smart_booking_engine.dto.response;

import com.moveinsync.smart_booking_engine.enums.ReasonCode;
import com.moveinsync.smart_booking_engine.enums.RuleOutcome;
import com.moveinsync.smart_booking_engine.enums.RuleType;
import java.util.Map;

/** Explain output for one evaluated rule. Contains no credentials or secrets. */
public record RuleResultResponse(
        RuleType ruleType,
        int priority,
        RuleOutcome outcome,
        ReasonCode reasonCode,
        String message,
        boolean hardDeny,
        Map<String, Object> details
) {
}
