package com.moveinsync.smart_booking_engine.service.rule;

import static com.moveinsync.smart_booking_engine.service.rule.RuleTestFixtures.config;
import static com.moveinsync.smart_booking_engine.service.rule.RuleTestFixtures.context;
import static com.moveinsync.smart_booking_engine.service.rule.RuleTestFixtures.employee;
import static com.moveinsync.smart_booking_engine.service.rule.RuleTestFixtures.jsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

import com.moveinsync.smart_booking_engine.entity.Employee;
import com.moveinsync.smart_booking_engine.enums.Gender;
import com.moveinsync.smart_booking_engine.enums.ReasonCode;
import com.moveinsync.smart_booking_engine.enums.RuleOutcome;
import com.moveinsync.smart_booking_engine.enums.RuleType;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class BookingCutoffRuleTest {

    private final BookingCutoffRule rule = new BookingCutoffRule(jsonMapper());
    private final Employee employee = employee(Gender.MAN, LocalTime.of(9, 0), LocalTime.of(18, 0));
    private final PolicyRuleConfig cfg = config(RuleType.BOOKING_CUTOFF, 30, true, "{\"cutoffMinutes\":60}");

    @Test
    void passWhenNowIsWellBeforeDeadline() {
        LocalDateTime service = LocalDateTime.of(2026, 10, 12, 9, 0);
        LocalDateTime now = LocalDateTime.of(2026, 10, 12, 7, 0);

        RuleResult result = rule.evaluate(context(employee, service, now), cfg);

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
    }

    @Test
    void failWithPastCutoffWhenNowIsAfterDeadline() {
        LocalDateTime service = LocalDateTime.of(2026, 10, 12, 9, 0);
        LocalDateTime now = LocalDateTime.of(2026, 10, 12, 8, 1);

        RuleResult result = rule.evaluate(context(employee, service, now), cfg);

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FAIL);
        assertThat(result.reasonCode()).isEqualTo(ReasonCode.PAST_CUTOFF);
        assertThat(result.details()).containsEntry("deadline", "2026-10-12T08:00");
    }

    @Test
    void boundaryIsInclusiveBookingExactlyAtDeadlineIsAllowed() {
        LocalDateTime service = LocalDateTime.of(2026, 10, 12, 9, 0);
        LocalDateTime now = LocalDateTime.of(2026, 10, 12, 8, 0);

        RuleResult result = rule.evaluate(context(employee, service, now), cfg);

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
    }

    @Test
    void zeroCutoffMinutesMeansNoRestriction() {
        PolicyRuleConfig cfg0 = config(RuleType.BOOKING_CUTOFF, 30, true, "{\"cutoffMinutes\":0}");
        LocalDateTime service = LocalDateTime.of(2026, 10, 12, 9, 0);
        LocalDateTime now = LocalDateTime.of(2026, 10, 12, 9, 0);

        assertThat(rule.evaluate(context(employee, service, now), cfg0).outcome()).isEqualTo(RuleOutcome.PASS);
    }

    @Test
    void invalidJsonFailsClosedAsHardDeny() {
        PolicyRuleConfig bad = config(RuleType.BOOKING_CUTOFF, 30, false, "not json");

        RuleResult result = rule.evaluate(context(employee, LocalDateTime.of(2026, 10, 12, 9, 0),
                LocalDateTime.of(2026, 10, 12, 7, 0)), bad);

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FAIL);
        assertThat(result.reasonCode()).isEqualTo(ReasonCode.INVALID_RULE_PARAMETERS);
        assertThat(result.hardDeny()).isTrue();
    }

    @Test
    void missingOrNegativeCutoffFailsClosed() {
        for (String json : new String[] {"{}", "{\"cutoffMinutes\":null}", "{\"cutoffMinutes\":-5}"}) {
            RuleResult result = rule.evaluate(context(employee, LocalDateTime.of(2026, 10, 12, 9, 0),
                    LocalDateTime.of(2026, 10, 12, 7, 0)), config(RuleType.BOOKING_CUTOFF, 30, true, json));

            assertThat(result.outcome()).as("json=%s", json).isEqualTo(RuleOutcome.FAIL);
            assertThat(result.reasonCode()).isEqualTo(ReasonCode.INVALID_RULE_PARAMETERS);
        }
    }
}
