package com.moveinsync.smart_booking_engine.service.rule;

import com.moveinsync.smart_booking_engine.enums.ReasonCode;
import com.moveinsync.smart_booking_engine.enums.RuleType;
import com.moveinsync.smart_booking_engine.repository.BookingRepository;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * A non-cancelled booking for the same employee, service slot and direction is a
 * duplicate. The read-only check here backs the partial unique index in the schema,
 * which is what actually prevents concurrent duplicate inserts. No parameters.
 */
@Component
public class DuplicateBookingRule implements BookingRule {

    private final BookingRepository bookingRepository;

    public DuplicateBookingRule(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Override
    public RuleType ruleType() {
        return RuleType.DUPLICATE_BOOKING;
    }

    @Override
    public RuleResult evaluate(RuleContext context, PolicyRuleConfig config) {
        boolean duplicate = bookingRepository.existsActiveForSlot(
                context.employee().getId(),
                context.bookingDateAndTime(),
                context.direction(),
                context.existingBookingId());

        Map<String, Object> details = Map.of(
                "requestedTime", context.bookingDateAndTime().toString(),
                "direction", context.direction().name());

        if (duplicate) {
            return RuleResult.fail(config, ReasonCode.DUPLICATE,
                    "An active booking already exists for this employee, service slot and direction",
                    details);
        }
        return RuleResult.pass(config, "No existing active booking for this service slot", details);
    }
}
