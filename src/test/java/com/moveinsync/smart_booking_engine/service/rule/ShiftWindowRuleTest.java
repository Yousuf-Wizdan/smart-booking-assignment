package com.moveinsync.smart_booking_engine.service.rule;

import static com.moveinsync.smart_booking_engine.service.rule.RuleTestFixtures.config;
import static com.moveinsync.smart_booking_engine.service.rule.RuleTestFixtures.context;
import static com.moveinsync.smart_booking_engine.service.rule.RuleTestFixtures.employee;
import static org.assertj.core.api.Assertions.assertThat;

import com.moveinsync.smart_booking_engine.entity.Employee;
import com.moveinsync.smart_booking_engine.enums.Gender;
import com.moveinsync.smart_booking_engine.enums.ReasonCode;
import com.moveinsync.smart_booking_engine.enums.RuleOutcome;
import com.moveinsync.smart_booking_engine.enums.RuleType;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class ShiftWindowRuleTest {

    private final ShiftWindowRule rule = new ShiftWindowRule();
    private final PolicyRuleConfig cfg = config(RuleType.SHIFT_WINDOW, 10, true, "{}");
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 10, 8, 0);

    @Test
    void passWhenServiceTimeInsideShift() {
        Employee employee = employee(Gender.MAN, LocalTime.of(9, 0), LocalTime.of(18, 0));
        RuleResult result = rule.evaluate(context(employee, LocalDateTime.of(2026, 10, 10, 10, 0), now), cfg);

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(result.reasonCode()).isNull();
    }

    @Test
    void failWithOutsideShiftWindowWhenServiceTimeOutsideShift() {
        Employee employee = employee(Gender.MAN, LocalTime.of(9, 0), LocalTime.of(18, 0));
        RuleResult result = rule.evaluate(context(employee, LocalDateTime.of(2026, 10, 10, 20, 0), now), cfg);

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FAIL);
        assertThat(result.reasonCode()).isEqualTo(ReasonCode.OUTSIDE_SHIFT_WINDOW);
        assertThat(result.hardDeny()).isTrue();
    }

    @Test
    void shiftBoundariesAreInclusive() {
        Employee employee = employee(Gender.MAN, LocalTime.of(9, 0), LocalTime.of(18, 0));

        assertThat(rule.evaluate(context(employee, LocalDateTime.of(2026, 10, 10, 9, 0), now), cfg).outcome())
                .isEqualTo(RuleOutcome.PASS);
        assertThat(rule.evaluate(context(employee, LocalDateTime.of(2026, 10, 10, 18, 0), now), cfg).outcome())
                .isEqualTo(RuleOutcome.PASS);
        assertThat(rule.evaluate(context(employee, LocalDateTime.of(2026, 10, 10, 8, 59), now), cfg).outcome())
                .isEqualTo(RuleOutcome.FAIL);
        assertThat(rule.evaluate(context(employee, LocalDateTime.of(2026, 10, 10, 18, 1), now), cfg).outcome())
                .isEqualTo(RuleOutcome.FAIL);
    }

    @Test
    void overnightShiftWrapsAroundMidnight() {
        Employee employee = employee(Gender.MAN, LocalTime.of(22, 0), LocalTime.of(6, 0));

        assertThat(employee.isOvernightShift()).isTrue();
        assertThat(rule.evaluate(context(employee, LocalDateTime.of(2026, 10, 10, 23, 30), now), cfg).outcome())
                .isEqualTo(RuleOutcome.PASS);
        assertThat(rule.evaluate(context(employee, LocalDateTime.of(2026, 10, 11, 3, 0), now), cfg).outcome())
                .isEqualTo(RuleOutcome.PASS);
        assertThat(rule.evaluate(context(employee, LocalDateTime.of(2026, 10, 10, 12, 0), now), cfg).outcome())
                .isEqualTo(RuleOutcome.FAIL);
        // Inclusive boundaries of the overnight window.
        assertThat(rule.evaluate(context(employee, LocalDateTime.of(2026, 10, 10, 22, 0), now), cfg).outcome())
                .isEqualTo(RuleOutcome.PASS);
        assertThat(rule.evaluate(context(employee, LocalDateTime.of(2026, 10, 11, 6, 0), now), cfg).outcome())
                .isEqualTo(RuleOutcome.PASS);
    }

    @Test
    void resultCarriesConfigMetadata() {
        Employee employee = employee(Gender.WOMAN, LocalTime.of(9, 0), LocalTime.of(18, 0));
        RuleResult result = rule.evaluate(context(employee, LocalDateTime.of(2026, 10, 10, 23, 0), now), cfg);

        assertThat(result.ruleType()).isEqualTo(RuleType.SHIFT_WINDOW);
        assertThat(result.priority()).isEqualTo(10);
        assertThat(result.details()).containsKeys("shiftStart", "shiftEnd", "serviceTime", "overnightShift");
    }
}
