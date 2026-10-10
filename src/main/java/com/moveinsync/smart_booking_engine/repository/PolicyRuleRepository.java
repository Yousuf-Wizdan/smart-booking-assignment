package com.moveinsync.smart_booking_engine.repository;

import com.moveinsync.smart_booking_engine.entity.PolicyRule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PolicyRuleRepository extends JpaRepository<PolicyRule, UUID> {

    /**
     * Deterministic execution order: priority, then rule type, then id (tie-breaker),
     * so the explain output is stable across runs. Written as explicit JPQL because
     * the derived-query parser reads {@code RuleTypeId} as a nested property path.
     */
    @Query("select r from PolicyRule r order by r.priority asc, r.ruleType asc, r.id asc")
    List<PolicyRule> findAllOrdered();

    @Query("select r from PolicyRule r where r.enabled = true"
            + " order by r.priority asc, r.ruleType asc, r.id asc")
    List<PolicyRule> findEnabledOrdered();
}
