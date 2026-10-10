package com.moveinsync.smart_booking_engine.dto.request;

import com.moveinsync.smart_booking_engine.enums.RuleType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Map;

/**
 * Parameters are rule-type specific; the policy service validates them against the
 * typed parameter record of the selected rule type.
 */
public record PolicyRuleCreateRequest(
        String businessUnit,
        String costCentreCode,
        @NotNull RuleType ruleType,
        Boolean enabled,
        @NotNull @PositiveOrZero Integer priority,
        Boolean hardDeny,
        Map<String, Object> parameters
) {
}
