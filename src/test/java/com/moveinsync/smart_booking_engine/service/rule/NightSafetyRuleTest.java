package com.moveinsync.smart_booking_engine.service.rule;

import static com.moveinsync.smart_booking_engine.service.rule.RuleTestFixtures.config;
import static com.moveinsync.smart_booking_engine.service.rule.RuleTestFixtures.employee;
import static com.moveinsync.smart_booking_engine.service.rule.RuleTestFixtures.jsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

import com.moveinsync.smart_booking_engine.entity.Employee;
import com.moveinsync.smart_booking_engine.enums.BookingDirection;
import com.moveinsync.smart_booking_engine.enums.Gender;
import com.moveinsync.smart_booking_engine.enums.ReasonCode;
import com.moveinsync.smart_booking_engine.enums.RoutePosition;
import com.moveinsync.smart_booking_engine.enums.RuleOutcome;
import com.moveinsync.smart_booking_engine.enums.RuleType;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class NightSafetyRuleTest {

    private static final String NIGHT_PARAMS =
            "{\"nightStart\":\"22:00\",\"nightEnd\":\"05:00\",\"requireEscort\":true}";

    private final NightSafetyRule rule = new NightSafetyRule(jsonMapper());
    private final PolicyRuleConfig cfg = config(RuleType.NIGHT_SAFETY, 20, true, NIGHT_PARAMS);
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 10, 20, 0);

    private RuleContext context(Gender gender, LocalDateTime serviceTime,
                                RoutePosition position, boolean escortAvailable) {
        Employee employee = employee(gender, LocalTime.of(21, 0), LocalTime.of(6, 0));
        return RuleContext.forRequest(employee, serviceTime, BookingDirection.LOGOUT,
                position, escortAvailable, now);
    }

    @Test
    void failWhenWomanIsLastDropAtNightWithoutEscort() {
        RuleResult result = rule.evaluate(context(Gender.WOMAN,
                LocalDateTime.of(2026, 10, 10, 23, 0), RoutePosition.LAST_DROP, false), cfg);

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FAIL);
        assertThat(result.reasonCode()).isEqualTo(ReasonCode.NIGHT_SAFETY);
    }

    @Test
    void passWhenEscortIsAvailable() {
        RuleResult result = rule.evaluate(context(Gender.WOMAN,
                LocalDateTime.of(2026, 10, 10, 23, 0), RoutePosition.FIRST_PICKUP, true), cfg);

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(result.details()).containsEntry("escortAvailable", true);
    }

    @Test
    void skipWhenNotAWoman() {
        assertThat(rule.evaluate(context(Gender.MAN,
                LocalDateTime.of(2026, 10, 10, 23, 0), RoutePosition.LAST_DROP, false), cfg).outcome())
                .isEqualTo(RuleOutcome.SKIPPED);
    }

    @Test
    void skipWhenRoutePositionIsNotFirstPickupOrLastDrop() {
        assertThat(rule.evaluate(context(Gender.WOMAN,
                LocalDateTime.of(2026, 10, 10, 23, 0), RoutePosition.OTHER, false), cfg).outcome())
                .isEqualTo(RuleOutcome.SKIPPED);
    }

    @Test
    void skipOutsideNightWindow() {
        assertThat(rule.evaluate(context(Gender.WOMAN,
                LocalDateTime.of(2026, 10, 10, 12, 0), RoutePosition.LAST_DROP, false), cfg).outcome())
                .isEqualTo(RuleOutcome.SKIPPED);
    }

    @Test
    void nightWindowCrossingMidnightCoversBothSides() {
        // Window is 22:00-05:00: 23:30 (before midnight) and 04:30 (after midnight) are inside.
        assertThat(rule.evaluate(context(Gender.WOMAN,
                LocalDateTime.of(2026, 10, 10, 23, 30), RoutePosition.FIRST_PICKUP, false), cfg).outcome())
                .isEqualTo(RuleOutcome.FAIL);
        assertThat(rule.evaluate(context(Gender.WOMAN,
                LocalDateTime.of(2026, 10, 11, 4, 30), RoutePosition.FIRST_PICKUP, false), cfg).outcome())
                .isEqualTo(RuleOutcome.FAIL);
        // 05:00 is the inclusive end of the window; 22:00 the inclusive start.
        assertThat(rule.evaluate(context(Gender.WOMAN,
                LocalDateTime.of(2026, 10, 11, 5, 0), RoutePosition.FIRST_PICKUP, true), cfg).outcome())
                .isEqualTo(RuleOutcome.PASS);
        assertThat(rule.evaluate(context(Gender.WOMAN,
                LocalDateTime.of(2026, 10, 10, 22, 0), RoutePosition.FIRST_PICKUP, true), cfg).outcome())
                .isEqualTo(RuleOutcome.PASS);
        // 05:01 and 21:59 are outside.
        assertThat(rule.evaluate(context(Gender.WOMAN,
                LocalDateTime.of(2026, 10, 11, 5, 1), RoutePosition.LAST_DROP, false), cfg).outcome())
                .isEqualTo(RuleOutcome.SKIPPED);
        assertThat(rule.evaluate(context(Gender.WOMAN,
                LocalDateTime.of(2026, 10, 10, 21, 59), RoutePosition.LAST_DROP, false), cfg).outcome())
                .isEqualTo(RuleOutcome.SKIPPED);
    }

    @Test
    void escortNotRequiredPassesWithoutEscort() {
        PolicyRuleConfig cfgNoEscort = config(RuleType.NIGHT_SAFETY, 20, true,
                "{\"nightStart\":\"22:00\",\"nightEnd\":\"05:00\",\"requireEscort\":false}");

        RuleResult result = rule.evaluate(context(Gender.WOMAN,
                LocalDateTime.of(2026, 10, 10, 23, 0), RoutePosition.LAST_DROP, false), cfgNoEscort);

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
    }

    @Test
    void invalidOrMissingParamsFailClosed() {
        for (String json : new String[] {"not json", "{}", "{\"nightStart\":\"22:00\"}"}) {
            RuleResult result = rule.evaluate(context(Gender.WOMAN,
                    LocalDateTime.of(2026, 10, 10, 23, 0), RoutePosition.LAST_DROP, false),
                    config(RuleType.NIGHT_SAFETY, 20, true, json));

            assertThat(result.outcome()).as("json=%s", json).isEqualTo(RuleOutcome.FAIL);
            assertThat(result.reasonCode()).isEqualTo(ReasonCode.INVALID_RULE_PARAMETERS);
            assertThat(result.hardDeny()).isTrue();
        }
    }
}
