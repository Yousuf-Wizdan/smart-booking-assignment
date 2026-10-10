package com.moveinsync.smart_booking_engine.service.rule;

import static com.moveinsync.smart_booking_engine.service.rule.RuleTestFixtures.config;
import static com.moveinsync.smart_booking_engine.service.rule.RuleTestFixtures.context;
import static com.moveinsync.smart_booking_engine.service.rule.RuleTestFixtures.employee;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moveinsync.smart_booking_engine.entity.CostCentreQuota;
import com.moveinsync.smart_booking_engine.entity.Employee;
import com.moveinsync.smart_booking_engine.enums.Gender;
import com.moveinsync.smart_booking_engine.enums.ReasonCode;
import com.moveinsync.smart_booking_engine.enums.RuleOutcome;
import com.moveinsync.smart_booking_engine.enums.RuleType;
import com.moveinsync.smart_booking_engine.repository.CostCentreQuotaRepository;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CostCentreQuotaRuleTest {

    private final CostCentreQuotaRepository quotaRepository = mock(CostCentreQuotaRepository.class);
    private final CostCentreQuotaRule rule = new CostCentreQuotaRule(quotaRepository);
    private final PolicyRuleConfig cfg = config(RuleType.COST_CENTRE_QUOTA, 50, true, "{}");
    private final Employee employee = employee(Gender.MAN, LocalTime.of(9, 0), LocalTime.of(18, 0));
    private final LocalDateTime service = LocalDateTime.of(2026, 10, 12, 9, 0);
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 10, 12, 0);

    @Test
    void failWithQuotaExceededWhenNoFreeSeat() {
        CostCentreQuota quota = new CostCentreQuota("CC-100", 1);
        quota.setReservedSeats(1);
        when(quotaRepository.findByCostCentreCode("CC-100")).thenReturn(Optional.of(quota));

        RuleResult result = rule.evaluate(context(employee, service, now), cfg);

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FAIL);
        assertThat(result.reasonCode()).isEqualTo(ReasonCode.QUOTA_EXCEEDED);
        assertThat(result.details()).containsEntry("capacity", 1).containsEntry("reservedSeats", 1);
    }

    @Test
    void passWhenFreeSeatsAvailable() {
        CostCentreQuota quota = new CostCentreQuota("CC-100", 4);
        quota.setReservedSeats(2);
        when(quotaRepository.findByCostCentreCode("CC-100")).thenReturn(Optional.of(quota));

        RuleResult result = rule.evaluate(context(employee, service, now), cfg);

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
    }

    @Test
    void failWithQuotaNotConfiguredWhenQuotaMissing() {
        when(quotaRepository.findByCostCentreCode("CC-100")).thenReturn(Optional.empty());

        RuleResult result = rule.evaluate(context(employee, service, now), cfg);

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FAIL);
        assertThat(result.reasonCode()).isEqualTo(ReasonCode.QUOTA_NOT_CONFIGURED);
    }

    @Test
    void isReadOnlyNeverReservesSeats() {
        CostCentreQuota quota = new CostCentreQuota("CC-100", 1);
        when(quotaRepository.findByCostCentreCode("CC-100")).thenReturn(Optional.of(quota));

        rule.evaluate(context(employee, service, now), cfg);

        verify(quotaRepository).findByCostCentreCode("CC-100");
        assertThat(quota.getReservedSeats()).isZero();
    }
}
