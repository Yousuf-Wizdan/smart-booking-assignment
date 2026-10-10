package com.moveinsync.smart_booking_engine.entity;

import com.moveinsync.smart_booking_engine.enums.RuleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * One configurable policy rule row.
 *
 * <p>Scoping (documented choice): {@code businessUnit == null} matches every business
 * unit, {@code costCentreCode == null} matches every cost centre within the matched
 * business unit. A rule applies to a booking when both scopes match (null = wildcard).
 *
 * <p>{@code parametersJson} holds rule-type-specific JSON parameters (e.g.
 * {@code {"cutoffMinutes":60}}). It is stored as text and parsed into typed parameter
 * records per rule type; invalid or missing parameters make the rule FAIL closed.
 */
@Entity
@Table(name = "policy_rule")
public class PolicyRule {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "business_unit", length = 64)
    private String businessUnit;

    @Column(name = "cost_centre_code", length = 32)
    private String costCentreCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 32)
    private RuleType ruleType;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** Lower number executes first in the explain output. */
    @Column(name = "priority", nullable = false)
    private int priority;

    /** A failing hard-deny rule forces {@code DENY} regardless of any soft failures. */
    @Column(name = "hard_deny", nullable = false)
    private boolean hardDeny;

    @Column(name = "parameters_json", nullable = false, columnDefinition = "text")
    private String parametersJson;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PolicyRule() {
    }

    public PolicyRule(String businessUnit, String costCentreCode, RuleType ruleType,
                      boolean enabled, int priority, boolean hardDeny, String parametersJson) {
        this.id = UUID.randomUUID();
        this.businessUnit = businessUnit;
        this.costCentreCode = costCentreCode;
        this.ruleType = ruleType;
        this.enabled = enabled;
        this.priority = priority;
        this.hardDeny = hardDeny;
        this.parametersJson = parametersJson;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** Null scope acts as a wildcard. */
    public boolean appliesTo(String employeeBusinessUnit, String employeeCostCentreCode) {
        boolean businessUnitMatches = businessUnit == null || businessUnit.equals(employeeBusinessUnit);
        boolean costCentreMatches = costCentreCode == null || costCentreCode.equals(employeeCostCentreCode);
        return businessUnitMatches && costCentreMatches;
    }

    public UUID getId() {
        return id;
    }

    public String getBusinessUnit() {
        return businessUnit;
    }

    public String getCostCentreCode() {
        return costCentreCode;
    }

    public RuleType getRuleType() {
        return ruleType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isHardDeny() {
        return hardDeny;
    }

    public String getParametersJson() {
        return parametersJson;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setBusinessUnit(String businessUnit) {
        this.businessUnit = businessUnit;
    }

    public void setCostCentreCode(String costCentreCode) {
        this.costCentreCode = costCentreCode;
    }

    public void setRuleType(RuleType ruleType) {
        this.ruleType = ruleType;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public void setHardDeny(boolean hardDeny) {
        this.hardDeny = hardDeny;
    }

    public void setParametersJson(String parametersJson) {
        this.parametersJson = parametersJson;
    }
}
