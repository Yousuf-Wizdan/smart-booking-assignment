package com.moveinsync.smart_booking_engine.dto.response;

import com.moveinsync.smart_booking_engine.entity.PolicyRule;
import com.moveinsync.smart_booking_engine.enums.RuleType;
import java.util.Map;
import java.util.UUID;

public record PolicyRuleResponse(
        UUID id,
        String businessUnit,
        String costCentreCode,
        RuleType ruleType,
        boolean enabled,
        int priority,
        boolean hardDeny,
        Map<String, Object> parameters,
        long version
) {

    public static PolicyRuleResponse from(PolicyRule rule, Map<String, Object> parameters) {
        return new PolicyRuleResponse(
                rule.getId(),
                rule.getBusinessUnit(),
                rule.getCostCentreCode(),
                rule.getRuleType(),
                rule.isEnabled(),
                rule.getPriority(),
                rule.isHardDeny(),
                parameters,
                rule.getVersion());
    }
}
