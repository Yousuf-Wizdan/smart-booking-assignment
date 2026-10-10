package com.moveinsync.smart_booking_engine.service.rule;

import com.moveinsync.smart_booking_engine.enums.ReasonCode;
import com.moveinsync.smart_booking_engine.enums.RuleType;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The requested service time must be inside the employee's shift window.
 * Shift boundaries are inclusive; overnight shifts (start after end) are supported.
 * No parameters.
 */
@Component
public class ShiftWindowRule implements BookingRule {

    @Override
    public RuleType ruleType() {
        return RuleType.SHIFT_WINDOW;
    }

    @Override
    public RuleResult evaluate(RuleContext context, PolicyRuleConfig config) {
        var employee = context.employee();
        var serviceTime = context.bookingDateAndTime().toLocalTime();
        Map<String, Object> details = Map.of(
                "shiftStart", employee.getShiftStart().toString(),
                "shiftEnd", employee.getShiftEnd().toString(),
                "serviceTime", serviceTime.toString(),
                "overnightShift", employee.isOvernightShift());

        if (employee.isWithinShift(serviceTime)) {
            return RuleResult.pass(config, "Service time is inside the employee's shift window", details);
        }
        return RuleResult.fail(config, ReasonCode.OUTSIDE_SHIFT_WINDOW,
                "Service time is outside the employee's shift window", details);
    }
}
