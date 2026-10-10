package com.moveinsync.smart_booking_engine.service.rule;

import tools.jackson.databind.ObjectMapper;
import com.moveinsync.smart_booking_engine.enums.ReasonCode;
import com.moveinsync.smart_booking_engine.enums.RuleType;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * A booking must be made at least {@code cutoffMinutes} before the service time.
 * The boundary is inclusive: a booking exactly at the deadline is still allowed;
 * only {@code now > deadline} fails. Parameters: {@code {"cutoffMinutes":60}}.
 */
@Component
public class BookingCutoffRule implements BookingRule {

    private final ObjectMapper objectMapper;

    public BookingCutoffRule(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public RuleType ruleType() {
        return RuleType.BOOKING_CUTOFF;
    }

    /** {@code cutoffMinutes} must be present and non-negative. */
    public record Params(Integer cutoffMinutes) {
    }

    @Override
    public RuleResult evaluate(RuleContext context, PolicyRuleConfig config) {
        Params params;
        try {
            params = objectMapper.readValue(config.parametersJson(), Params.class);
        } catch (Exception e) {
            return RuleResult.invalidConfig(config, "BOOKING_CUTOFF parameters are not valid JSON: " + e.getMessage());
        }
        if (params == null || params.cutoffMinutes() == null || params.cutoffMinutes() < 0) {
            return RuleResult.invalidConfig(config, "BOOKING_CUTOFF requires a non-negative 'cutoffMinutes'");
        }

        LocalDateTime deadline = context.bookingDateAndTime().minusMinutes(params.cutoffMinutes());
        Map<String, Object> details = Map.of(
                "cutoffMinutes", params.cutoffMinutes(),
                "deadline", deadline.toString(),
                "now", context.now().toString());

        if (context.now().isAfter(deadline)) {
            return RuleResult.fail(config, ReasonCode.PAST_CUTOFF,
                    "Booking attempted after the cut-off of " + params.cutoffMinutes()
                            + " minutes before service time", details);
        }
        return RuleResult.pass(config, "Booking is before the cut-off deadline", details);
    }
}
