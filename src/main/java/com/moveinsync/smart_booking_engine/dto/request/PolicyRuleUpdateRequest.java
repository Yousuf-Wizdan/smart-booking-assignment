package com.moveinsync.smart_booking_engine.dto.request;

import com.moveinsync.smart_booking_engine.enums.RuleType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Map;

/** Full update of a policy rule (PUT semantics: all fields supplied). */
public record PolicyRuleUpdateRequest(
        String businessUnit,
        String costCentreCode,
        @NotNull RuleType ruleType,
        Boolean enabled,
        @NotNull @PositiveOrZero Integer priority,
        Boolean hardDeny,
        Map<String, Object> parameters
) {
}
