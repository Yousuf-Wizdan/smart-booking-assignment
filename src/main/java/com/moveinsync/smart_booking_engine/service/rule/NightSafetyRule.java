package com.moveinsync.smart_booking_engine.service.rule;

import tools.jackson.databind.ObjectMapper;
import com.moveinsync.smart_booking_engine.enums.Gender;
import com.moveinsync.smart_booking_engine.enums.ReasonCode;
import com.moveinsync.smart_booking_engine.enums.RoutePosition;
import com.moveinsync.smart_booking_engine.enums.RuleType;
import java.time.LocalTime;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Gender night-safety rule: a woman employee must not be the first pickup or the
 * last drop during the configured night hours unless an escort is available.
 *
 * <p>Route position comes from the explicit {@link RoutePosition} input; it is never
 * inferred from pickup/drop text (modelling assumption, see README). The night
 * window may cross midnight (e.g. 22:00-05:00).
 *
 * <p>Parameters: {@code {"nightStart":"22:00","nightEnd":"05:00","requireEscort":true}}.
 */
@Component
public class NightSafetyRule implements BookingRule {

    private final ObjectMapper objectMapper;

    public NightSafetyRule(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public RuleType ruleType() {
        return RuleType.NIGHT_SAFETY;
    }

    public record Params(LocalTime nightStart, LocalTime nightEnd, Boolean requireEscort) {
    }

    @Override
    public RuleResult evaluate(RuleContext context, PolicyRuleConfig config) {
        Params params;
        try {
            params = objectMapper.readValue(config.parametersJson(), Params.class);
        } catch (Exception e) {
            return RuleResult.invalidConfig(config, "NIGHT_SAFETY parameters are not valid JSON: " + e.getMessage());
        }
        if (params == null || params.nightStart() == null || params.nightEnd() == null
                || params.requireEscort() == null) {
            return RuleResult.invalidConfig(config,
                    "NIGHT_SAFETY requires 'nightStart', 'nightEnd' and 'requireEscort'");
        }

        boolean woman = context.employee().getGender() == Gender.WOMAN;
        boolean firstPickupOrLastDrop = context.routePosition() == RoutePosition.FIRST_PICKUP
                || context.routePosition() == RoutePosition.LAST_DROP;
        LocalTime serviceTime = context.bookingDateAndTime().toLocalTime();
        boolean withinNightWindow = isWithinNightWindow(serviceTime, params.nightStart(), params.nightEnd());

        Map<String, Object> details = Map.of(
                "nightStart", params.nightStart().toString(),
                "nightEnd", params.nightEnd().toString(),
                "serviceTime", serviceTime.toString(),
                "routePosition", context.routePosition().name(),
                "escortAvailable", context.escortAvailable());

        if (!woman || !firstPickupOrLastDrop || !withinNightWindow) {
            return RuleResult.skip(config,
                    "Night-safety rule does not apply (not a woman first-pickup/last-drop trip in night hours)");
        }
        if (params.requireEscort() && !context.escortAvailable()) {
            return RuleResult.fail(config, ReasonCode.NIGHT_SAFETY,
                    "Woman employee cannot be first pickup or last drop at night without an escort",
                    details);
        }
        return RuleResult.pass(config, "Escort available (or not required) for night trip", details);
    }

    /** Night window start/end are inclusive; a window where start == end covers the full day. */
    private boolean isWithinNightWindow(LocalTime time, LocalTime start, LocalTime end) {
        if (start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !time.isBefore(start) && !time.isAfter(end);
        }
        // Window crosses midnight, e.g. 22:00-05:00.
        return !time.isBefore(start) || !time.isAfter(end);
    }
}
