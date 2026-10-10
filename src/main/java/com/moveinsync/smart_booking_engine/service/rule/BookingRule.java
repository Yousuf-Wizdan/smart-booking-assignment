package com.moveinsync.smart_booking_engine.service.rule;

import com.moveinsync.smart_booking_engine.enums.RuleType;

/**
 * Strategy interface of the rule engine: one implementation per {@link RuleType}.
 * Implementations are stateless Spring beans; configuration comes from the
 * {@link PolicyRuleConfig} passed in for each evaluation, so policy changes take
 * effect on the next evaluation without a restart.
 */
public interface BookingRule {

    RuleType ruleType();

    /** Must never mutate bookings or quota: evaluation is advisory. */
    RuleResult evaluate(RuleContext context, PolicyRuleConfig config);
}
