package com.moveinsync.smart_booking_engine.service.rule;

import static com.moveinsync.smart_booking_engine.service.rule.RuleTestFixtures.context;
import static com.moveinsync.smart_booking_engine.service.rule.RuleTestFixtures.employee;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moveinsync.smart_booking_engine.entity.Employee;
import com.moveinsync.smart_booking_engine.entity.PolicyRule;
import com.moveinsync.smart_booking_engine.enums.Decision;
import com.moveinsync.smart_booking_engine.enums.Gender;
import com.moveinsync.smart_booking_engine.enums.ReasonCode;
import com.moveinsync.smart_booking_engine.enums.RuleOutcome;
import com.moveinsync.smart_booking_engine.enums.RuleType;
import com.moveinsync.smart_booking_engine.repository.PolicyRuleRepository;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Precedence and determinism tests for {@link RuleEngine}, using stub strategies so
 * the assertions are about the engine, not about individual rules.
 */
class RuleEngineTest {

    /** A stub strategy returning a fixed outcome, recording the order it ran in. */
    private record StubRule(RuleType ruleType, RuleOutcome outcome, ReasonCode reasonCode,
                            boolean hardDeny, List<RuleType> callOrder) implements BookingRule {
        @Override
        public RuleResult evaluate(RuleContext context, PolicyRuleConfig config) {
            callOrder.add(ruleType);
            return switch (outcome) {
                case PASS -> RuleResult.pass(config, "stub pass", null);
                case SKIPPED -> RuleResult.skip(config, "stub skip");
                case FAIL -> RuleResult.fail(config, reasonCode, "stub fail", null);
            };
        }
    }

    private final PolicyRuleRepository repository = mock(PolicyRuleRepository.class);
    private final List<RuleType> callOrder = new ArrayList<>();
    private final Employee employee = employee(Gender.MAN, LocalTime.of(9, 0), LocalTime.of(18, 0));
    private final LocalDateTime service = LocalDateTime.of(2026, 10, 12, 9, 0);
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 10, 12, 0);

    private PolicyRule rule(RuleType type, int priority, boolean hardDeny) {
        return new PolicyRule(null, null, type, true, priority, hardDeny, "{}");
    }

    private EngineResult run(List<PolicyRule> policyRules, List<BookingRule> strategies) {
        when(repository.findEnabledOrdered()).thenReturn(policyRules);
        return new RuleEngine(repository, strategies).evaluate(context(employee, service, now));
    }

    @Test
    void allRulesPassMeansAllow() {
        EngineResult result = run(
                List.of(rule(RuleType.SHIFT_WINDOW, 10, true), rule(RuleType.COST_CENTRE_QUOTA, 50, true)),
                List.of(new StubRule(RuleType.SHIFT_WINDOW, RuleOutcome.PASS, null, true, callOrder),
                        new StubRule(RuleType.COST_CENTRE_QUOTA, RuleOutcome.PASS, null, true, callOrder)));

        assertThat(result.decision()).isEqualTo(Decision.ALLOW);
        assertThat(result.decidingReason()).isNull();
        assertThat(result.failureReasons()).isEmpty();
    }

    @Test
    void anyHardFailureMeansDeny() {
        EngineResult result = run(
                List.of(rule(RuleType.SHIFT_WINDOW, 10, true), rule(RuleType.NIGHT_SAFETY, 20, true)),
                List.of(new StubRule(RuleType.SHIFT_WINDOW, RuleOutcome.FAIL, ReasonCode.OUTSIDE_SHIFT_WINDOW, true, callOrder),
                        new StubRule(RuleType.NIGHT_SAFETY, RuleOutcome.FAIL, ReasonCode.NIGHT_SAFETY, true, callOrder)));

        assertThat(result.decision()).isEqualTo(Decision.DENY);
        assertThat(result.decidingReason()).isEqualTo(ReasonCode.OUTSIDE_SHIFT_WINDOW);
    }

    @Test
    void onlySoftFailuresMeanNeedsApproval() {
        EngineResult result = run(
                List.of(rule(RuleType.BOOKING_CUTOFF, 30, false)),
                List.of(new StubRule(RuleType.BOOKING_CUTOFF, RuleOutcome.FAIL, ReasonCode.PAST_CUTOFF, false, callOrder)));

        assertThat(result.decision()).isEqualTo(Decision.NEEDS_APPROVAL);
        assertThat(result.decidingReason()).isEqualTo(ReasonCode.PAST_CUTOFF);
    }

    @Test
    void hardDenyBeatsSoftFailureRegardlessOfPriority() {
        // The soft rule has the lower priority number (runs first), yet the hard
        // failure must decide the outcome.
        EngineResult result = run(
                List.of(rule(RuleType.BOOKING_CUTOFF, 1, false), rule(RuleType.NIGHT_SAFETY, 99, true)),
                List.of(new StubRule(RuleType.BOOKING_CUTOFF, RuleOutcome.FAIL, ReasonCode.PAST_CUTOFF, false, callOrder),
                        new StubRule(RuleType.NIGHT_SAFETY, RuleOutcome.FAIL, ReasonCode.NIGHT_SAFETY, true, callOrder)));

        assertThat(result.decision()).isEqualTo(Decision.DENY);
        assertThat(result.decidingReason()).isEqualTo(ReasonCode.NIGHT_SAFETY);
    }

    @Test
    void evaluationOrderIsPriorityThenRuleTypeThenId() {
        PolicyRule highFirst = rule(RuleType.SHIFT_WINDOW, 5, true);
        PolicyRule tieA = rule(RuleType.BOOKING_CUTOFF, 10, true);
        PolicyRule tieB = rule(RuleType.NIGHT_SAFETY, 10, true);

        run(List.of(tieB, highFirst, tieA),
                List.of(new StubRule(RuleType.SHIFT_WINDOW, RuleOutcome.PASS, null, true, callOrder),
                        new StubRule(RuleType.BOOKING_CUTOFF, RuleOutcome.PASS, null, true, callOrder),
                        new StubRule(RuleType.NIGHT_SAFETY, RuleOutcome.PASS, null, true, callOrder)));

        // Priority 5 first; the two priority-10 rules break the tie on rule type name.
        assertThat(callOrder).containsExactly(RuleType.SHIFT_WINDOW, RuleType.BOOKING_CUTOFF, RuleType.NIGHT_SAFETY);
    }

    @Test
    void disabledAndOutOfScopeRulesDoNotRun() {
        PolicyRule disabled = rule(RuleType.SHIFT_WINDOW, 10, true);
        disabled.setEnabled(false);
        PolicyRule otherBu = new PolicyRule("BU-OTHER", null, RuleType.BOOKING_CUTOFF, true, 20, true, "{}");

        EngineResult result = run(List.of(disabled, otherBu),
                List.of(new StubRule(RuleType.SHIFT_WINDOW, RuleOutcome.FAIL, ReasonCode.OUTSIDE_SHIFT_WINDOW, true, callOrder),
                        new StubRule(RuleType.BOOKING_CUTOFF, RuleOutcome.FAIL, ReasonCode.PAST_CUTOFF, true, callOrder)));

        assertThat(callOrder).isEmpty();
        assertThat(result.ruleResults()).isEmpty();
        assertThat(result.decision()).isEqualTo(Decision.ALLOW);
    }

    @Test
    void failureReasonsAreDistinctAndInEvaluationOrder() {
        EngineResult result = run(
                List.of(rule(RuleType.SHIFT_WINDOW, 10, true),
                        rule(RuleType.BOOKING_CUTOFF, 20, true),
                        rule(RuleType.NIGHT_SAFETY, 30, true)),
                List.of(new StubRule(RuleType.SHIFT_WINDOW, RuleOutcome.FAIL, ReasonCode.OUTSIDE_SHIFT_WINDOW, true, callOrder),
                        new StubRule(RuleType.BOOKING_CUTOFF, RuleOutcome.FAIL, ReasonCode.PAST_CUTOFF, true, callOrder),
                        new StubRule(RuleType.NIGHT_SAFETY, RuleOutcome.FAIL, ReasonCode.NIGHT_SAFETY, true, callOrder)));

        assertThat(result.failureReasons())
                .containsExactly(ReasonCode.OUTSIDE_SHIFT_WINDOW, ReasonCode.PAST_CUTOFF, ReasonCode.NIGHT_SAFETY);
    }

    @Test
    void explainOutputContainsEveryRuleEvenWhenOneFails() {
        EngineResult result = run(
                List.of(rule(RuleType.SHIFT_WINDOW, 10, true), rule(RuleType.COST_CENTRE_QUOTA, 50, true)),
                List.of(new StubRule(RuleType.SHIFT_WINDOW, RuleOutcome.FAIL, ReasonCode.OUTSIDE_SHIFT_WINDOW, true, callOrder),
                        new StubRule(RuleType.COST_CENTRE_QUOTA, RuleOutcome.SKIPPED, null, true, callOrder)));

        assertThat(result.ruleResults()).hasSize(2);
        assertThat(result.decision()).isEqualTo(Decision.DENY);
    }

    @Test
    void ruleTypeWithoutRegisteredStrategyFailsClosedAsDeny() {
        EngineResult result = run(
                List.of(rule(RuleType.DUPLICATE_BOOKING, 10, true)),
                List.of(new StubRule(RuleType.SHIFT_WINDOW, RuleOutcome.PASS, null, true, callOrder)));

        assertThat(result.decision()).isEqualTo(Decision.DENY);
        assertThat(result.decidingReason()).isEqualTo(ReasonCode.INVALID_RULE_PARAMETERS);
        assertThat(result.ruleResults()).hasSize(1);
        assertThat(result.ruleResults().get(0).hardDeny()).isTrue();
    }
}
