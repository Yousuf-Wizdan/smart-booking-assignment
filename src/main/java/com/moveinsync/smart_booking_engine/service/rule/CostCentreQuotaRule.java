package com.moveinsync.smart_booking_engine.service.rule;

import com.moveinsync.smart_booking_engine.entity.CostCentreQuota;
import com.moveinsync.smart_booking_engine.enums.ReasonCode;
import com.moveinsync.smart_booking_engine.enums.RuleType;
import com.moveinsync.smart_booking_engine.repository.CostCentreQuotaRepository;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Read-only seat availability check for the employee's cost centre. The actual seat
 * reservation happens atomically in the booking transaction (conditional UPDATE);
 * this rule never decrements quota. No parameters.
 */
@Component
public class CostCentreQuotaRule implements BookingRule {

    private final CostCentreQuotaRepository quotaRepository;

    public CostCentreQuotaRule(CostCentreQuotaRepository quotaRepository) {
        this.quotaRepository = quotaRepository;
    }

    @Override
    public RuleType ruleType() {
        return RuleType.COST_CENTRE_QUOTA;
    }

    @Override
    public RuleResult evaluate(RuleContext context, PolicyRuleConfig config) {
        String costCentreCode = context.employee().getCostCentreCode();
        CostCentreQuota quota = quotaRepository.findByCostCentreCode(costCentreCode).orElse(null);

        if (quota == null) {
            return RuleResult.fail(config, ReasonCode.QUOTA_NOT_CONFIGURED,
                    "No seat quota is configured for cost centre " + costCentreCode,
                    Map.of("costCentreCode", costCentreCode));
        }

        Map<String, Object> details = Map.of(
                "costCentreCode", costCentreCode,
                "capacity", quota.getCapacity(),
                "reservedSeats", quota.getReservedSeats());

        if (!quota.hasFreeSeat()) {
            return RuleResult.fail(config, ReasonCode.QUOTA_EXCEEDED,
                    "No free seats left for cost centre " + costCentreCode, details);
        }
        return RuleResult.pass(config, "Free seats available for cost centre " + costCentreCode, details);
    }
}
