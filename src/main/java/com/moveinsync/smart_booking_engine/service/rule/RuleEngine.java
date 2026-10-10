package com.moveinsync.smart_booking_engine.service.rule;

import com.moveinsync.smart_booking_engine.entity.PolicyRule;
import com.moveinsync.smart_booking_engine.enums.Decision;
import com.moveinsync.smart_booking_engine.enums.ReasonCode;
import com.moveinsync.smart_booking_engine.enums.RuleOutcome;
import com.moveinsync.smart_booking_engine.enums.RuleType;
import com.moveinsync.smart_booking_engine.repository.PolicyRuleRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * The rule engine: loads the enabled, applicable policy rules, runs each one through
 * its {@link BookingRule} strategy in deterministic order, and derives one decision.
 *
 * <p>Precedence model (tested in {@code RuleEngineTest}):
 * <ol>
 *   <li>Every applicable rule runs — the explain output shows the whole stack.</li>
 *   <li>A failing hard-deny rule beats everything: decision {@code DENY}.</li>
 *   <li>Otherwise a failing soft rule (hardDeny=false) means {@code NEEDS_APPROVAL}.</li>
 *   <li>Otherwise {@code ALLOW}.</li>
 * </ol>
 * The deciding reason is the first failure in evaluation order within the winning
 * category; {@code failureReasons} lists every distinct failure code in that order.
 *
 * <p>Complexity: sorting is O(R log R) and evaluation is O(R) plus the database
 * lookups inside individual rules (R = applicable rules).
 */
@Service
public class RuleEngine {

    /** Deterministic order: priority, then rule type name, then policy row id. */
    private static final Comparator<PolicyRule> ORDER =
            Comparator.comparingInt(PolicyRule::getPriority)
                    .thenComparing(r -> r.getRuleType().name())
                    .thenComparing(PolicyRule::getId);

    private final PolicyRuleRepository policyRuleRepository;
    private final Map<RuleType, BookingRule> rulesByType;

    public RuleEngine(PolicyRuleRepository policyRuleRepository, List<BookingRule> bookingRules) {
        this.policyRuleRepository = policyRuleRepository;
        this.rulesByType = bookingRules.stream()
                .collect(Collectors.toMap(BookingRule::ruleType, Function.identity()));
    }

    public EngineResult evaluate(RuleContext context) {
        List<PolicyRule> applicable = policyRuleRepository
                .findEnabledOrdered().stream()
                .filter(PolicyRule::isEnabled)
                .filter(r -> r.appliesTo(context.employee().getBusinessUnit(),
                        context.employee().getCostCentreCode()))
                .sorted(ORDER)
                .toList();

        List<RuleResult> results = new ArrayList<>(applicable.size());
        for (PolicyRule rule : applicable) {
            BookingRule strategy = rulesByType.get(rule.getRuleType());
            if (strategy == null) {
                results.add(RuleResult.invalidConfig(PolicyRuleConfig.from(rule),
                        "No rule implementation registered for type " + rule.getRuleType()));
                continue;
            }
            results.add(strategy.evaluate(context, PolicyRuleConfig.from(rule)));
        }

        return decide(results);
    }

    private EngineResult decide(List<RuleResult> results) {
        List<RuleResult> hardFailures = results.stream()
                .filter(r -> r.outcome() == RuleOutcome.FAIL && r.hardDeny())
                .toList();
        if (!hardFailures.isEmpty()) {
            return build(Decision.DENY, hardFailures.get(0).reasonCode(), results);
        }

        List<RuleResult> softFailures = results.stream()
                .filter(r -> r.outcome() == RuleOutcome.FAIL && !r.hardDeny())
                .toList();
        if (!softFailures.isEmpty()) {
            return build(Decision.NEEDS_APPROVAL, softFailures.get(0).reasonCode(), results);
        }

        return build(Decision.ALLOW, null, results);
    }

    private EngineResult build(Decision decision, ReasonCode decidingReason, List<RuleResult> results) {
        // LinkedHashSet keeps evaluation order while removing duplicate codes.
        LinkedHashSet<ReasonCode> failureReasons = results.stream()
                .filter(r -> r.outcome() == RuleOutcome.FAIL)
                .map(RuleResult::reasonCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new EngineResult(decision, decidingReason, List.copyOf(failureReasons), results);
    }
}
