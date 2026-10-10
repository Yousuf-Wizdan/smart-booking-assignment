package com.moveinsync.smart_booking_engine.service.rule;

import static com.moveinsync.smart_booking_engine.service.rule.RuleTestFixtures.config;
import static com.moveinsync.smart_booking_engine.service.rule.RuleTestFixtures.context;
import static com.moveinsync.smart_booking_engine.service.rule.RuleTestFixtures.employee;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moveinsync.smart_booking_engine.entity.Employee;
import com.moveinsync.smart_booking_engine.enums.Gender;
import com.moveinsync.smart_booking_engine.enums.ReasonCode;
import com.moveinsync.smart_booking_engine.enums.RuleOutcome;
import com.moveinsync.smart_booking_engine.enums.RuleType;
import com.moveinsync.smart_booking_engine.repository.BookingRepository;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class DuplicateBookingRuleTest {

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final DuplicateBookingRule rule = new DuplicateBookingRule(bookingRepository);
    private final PolicyRuleConfig cfg = config(RuleType.DUPLICATE_BOOKING, 40, true, "{}");
    private final Employee employee = employee(Gender.MAN, LocalTime.of(9, 0), LocalTime.of(18, 0));
    private final LocalDateTime service = LocalDateTime.of(2026, 10, 12, 9, 0);
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 10, 12, 0);

    @Test
    void failWithDuplicateWhenActiveBookingExists() {
        when(bookingRepository.existsActiveForSlot(eq(employee.getId()), eq(service), any(), any()))
                .thenReturn(true);

        RuleResult result = rule.evaluate(context(employee, service, now), cfg);

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FAIL);
        assertThat(result.reasonCode()).isEqualTo(ReasonCode.DUPLICATE);
    }

    @Test
    void passWhenNoActiveBookingForSlot() {
        when(bookingRepository.existsActiveForSlot(eq(employee.getId()), eq(service), any(), any()))
                .thenReturn(false);

        RuleResult result = rule.evaluate(context(employee, service, now), cfg);

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
    }

    @Test
    void passesExistingBookingIdThroughSoItIsNotItsOwnDuplicate() {
        when(bookingRepository.existsActiveForSlot(eq(employee.getId()), eq(service), any(), any()))
                .thenReturn(false);

        rule.evaluate(context(employee, service, now), cfg);

        // forRequest passes null excludeBookingId; forBooking passes the booking's own id.
        verify(bookingRepository).existsActiveForSlot(employee.getId(), service,
                com.moveinsync.smart_booking_engine.enums.BookingDirection.LOGIN, null);
    }
}
