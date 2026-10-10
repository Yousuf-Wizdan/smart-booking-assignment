package com.moveinsync.smart_booking_engine.service.rule;

import com.moveinsync.smart_booking_engine.enums.ReasonCode;
import com.moveinsync.smart_booking_engine.enums.RuleOutcome;
import com.moveinsync.smart_booking_engine.enums.RuleType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Outcome of evaluating a single policy rule row.
 *
 * @param hardDeny true when a failure of this rule forces {@code DENY} regardless
 *                 of any soft failures (a misconfigured rule fails closed and is
 *                 always treated as hard-deny)
 */
public record RuleResult(
        RuleType ruleType,
        int priority,
        RuleOutcome outcome,
        ReasonCode reasonCode,
        String message,
        boolean hardDeny,
        Map<String, Object> details
) {

    public static RuleResult pass(PolicyRuleConfig config, String message, Map<String, Object> details) {
        return new RuleResult(config.ruleType(), config.priority(), RuleOutcome.PASS,
                null, message, config.hardDeny(), safe(details));
    }

    public static RuleResult skip(PolicyRuleConfig config, String message) {
        return new RuleResult(config.ruleType(), config.priority(), RuleOutcome.SKIPPED,
                null, message, config.hardDeny(), Map.of());
    }

    public static RuleResult fail(PolicyRuleConfig config, ReasonCode reasonCode, String message,
                                  Map<String, Object> details) {
        return new RuleResult(config.ruleType(), config.priority(), RuleOutcome.FAIL,
                reasonCode, message, config.hardDeny(), safe(details));
    }

    /** Fails closed: a broken rule configuration is always a hard deny. */
    public static RuleResult invalidConfig(PolicyRuleConfig config, String message) {
        return new RuleResult(config.ruleType(), config.priority(), RuleOutcome.FAIL,
                ReasonCode.INVALID_RULE_PARAMETERS, message, true, Map.of());
    }

    private static Map<String, Object> safe(Map<String, Object> details) {
        return details == null ? Map.of() : new LinkedHashMap<>(details);
    }
}
