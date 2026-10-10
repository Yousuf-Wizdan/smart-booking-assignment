package com.moveinsync.smart_booking_engine.service.rule;

import com.moveinsync.smart_booking_engine.entity.Employee;
import com.moveinsync.smart_booking_engine.enums.BookingDirection;
import com.moveinsync.smart_booking_engine.enums.Gender;
import com.moveinsync.smart_booking_engine.enums.RoutePosition;
import com.moveinsync.smart_booking_engine.enums.RuleType;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Shared builders for the rule unit tests. */
final class RuleTestFixtures {

    private RuleTestFixtures() {
    }

    static ObjectMapper jsonMapper() {
        return JsonMapper.builder().build();
    }

    static Employee employee(Gender gender, LocalTime shiftStart, LocalTime shiftEnd) {
        return new Employee("E-1", "Test Employee", "BU-1", "CC-100", gender, shiftStart, shiftEnd);
    }

    static PolicyRuleConfig config(RuleType ruleType, int priority, boolean hardDeny, String parametersJson) {
        return new PolicyRuleConfig(UUID.randomUUID(), ruleType, priority, hardDeny, parametersJson);
    }

    static RuleContext context(Employee employee, LocalDateTime serviceTime, LocalDateTime now) {
        return RuleContext.forRequest(employee, serviceTime, BookingDirection.LOGIN,
                RoutePosition.OTHER, false, now);
    }
}
