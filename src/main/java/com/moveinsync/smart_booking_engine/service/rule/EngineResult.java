package com.moveinsync.smart_booking_engine.service.rule;

import com.moveinsync.smart_booking_engine.enums.Decision;
import com.moveinsync.smart_booking_engine.enums.ReasonCode;
import java.util.List;

/**
 * The final outcome of one rule stack evaluation.
 *
 * @param failureReasons distinct reason codes of failing rules, in evaluation order
 */
public record EngineResult(
        Decision decision,
        ReasonCode decidingReason,
        List<ReasonCode> failureReasons,
        List<RuleResult> ruleResults
) {
}
