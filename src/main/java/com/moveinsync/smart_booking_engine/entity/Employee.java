package com.moveinsync.smart_booking_engine.entity;

import com.moveinsync.smart_booking_engine.enums.Gender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * An employee who can book office transport. Business times (shift window) are
 * wall-clock times in the configured company timezone.
 */
@Entity
@Table(name = "employee")
public class Employee {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "employee_code", nullable = false, unique = true, length = 32)
    private String employeeCode;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "business_unit", nullable = false, length = 64)
    private String businessUnit;

    @Column(name = "cost_centre_code", nullable = false, length = 32)
    private String costCentreCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 16)
    private Gender gender;

    @Column(name = "shift_start", nullable = false)
    private LocalTime shiftStart;

    @Column(name = "shift_end", nullable = false)
    private LocalTime shiftEnd;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Employee() {
    }

    public Employee(String employeeCode, String fullName, String businessUnit,
                    String costCentreCode, Gender gender, LocalTime shiftStart, LocalTime shiftEnd) {
        this.id = UUID.randomUUID();
        this.employeeCode = employeeCode;
        this.fullName = fullName;
        this.businessUnit = businessUnit;
        this.costCentreCode = costCentreCode;
        this.gender = gender;
        this.shiftStart = shiftStart;
        this.shiftEnd = shiftEnd;
        this.active = true;
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

    /**
     * A shift whose start is after its end is an overnight shift (e.g. 22:00-06:00).
     * Shift boundaries are inclusive: a service exactly at shiftStart or shiftEnd is inside.
     */
    public boolean isOvernightShift() {
        return shiftStart.isAfter(shiftEnd);
    }

    public boolean isWithinShift(LocalTime serviceTime) {
        if (isOvernightShift()) {
            return !serviceTime.isBefore(shiftStart) || !serviceTime.isAfter(shiftEnd);
        }
        return !serviceTime.isBefore(shiftStart) && !serviceTime.isAfter(shiftEnd);
    }

    public UUID getId() {
        return id;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getFullName() {
        return fullName;
    }

    public String getBusinessUnit() {
        return businessUnit;
    }

    public String getCostCentreCode() {
        return costCentreCode;
    }

    public Gender getGender() {
        return gender;
    }

    public LocalTime getShiftStart() {
        return shiftStart;
    }

    public LocalTime getShiftEnd() {
        return shiftEnd;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setBusinessUnit(String businessUnit) {
        this.businessUnit = businessUnit;
    }

    public void setCostCentreCode(String costCentreCode) {
        this.costCentreCode = costCentreCode;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public void setShiftStart(LocalTime shiftStart) {
        this.shiftStart = shiftStart;
    }

    public void setShiftEnd(LocalTime shiftEnd) {
        this.shiftEnd = shiftEnd;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
