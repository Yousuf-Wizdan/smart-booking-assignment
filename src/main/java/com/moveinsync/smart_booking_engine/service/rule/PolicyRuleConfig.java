package com.moveinsync.smart_booking_engine.service.rule;

import com.moveinsync.smart_booking_engine.entity.PolicyRule;
import com.moveinsync.smart_booking_engine.enums.RuleType;
import java.util.UUID;

/**
 * Immutable view of one {@link PolicyRule} row as the rules consume it.
 * Also used by {@link RuleResult} factories so results carry the config metadata.
 */
public record PolicyRuleConfig(
        UUID id,
        RuleType ruleType,
        int priority,
        boolean hardDeny,
        String parametersJson
) {

    public static PolicyRuleConfig from(PolicyRule rule) {
        return new PolicyRuleConfig(rule.getId(), rule.getRuleType(), rule.getPriority(),
                rule.isHardDeny(), rule.getParametersJson());
    }
}
