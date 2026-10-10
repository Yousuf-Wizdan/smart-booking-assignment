package com.moveinsync.smart_booking_engine.entity;

import com.moveinsync.smart_booking_engine.enums.BookingDirection;
import com.moveinsync.smart_booking_engine.enums.BookingStatus;
import com.moveinsync.smart_booking_engine.enums.Decision;
import com.moveinsync.smart_booking_engine.enums.ReasonCode;
import com.moveinsync.smart_booking_engine.enums.RoutePosition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A cab/shuttle booking for one employee on one service slot.
 *
 * <p>{@code requestedTime} is the service date-time (wall clock in the configured
 * company timezone) and always falls on {@code bookingDate}. Duplicate detection uses
 * (employee, requestedTime, direction); a partial unique index in the schema enforces
 * this for non-terminal bookings.
 */
@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16)
    private BookingDirection direction;

    @Column(name = "pickup_location", nullable = false, length = 120)
    private String pickupLocation;

    @Column(name = "drop_location", nullable = false, length = 120)
    private String dropLocation;

    @Column(name = "requested_time", nullable = false)
    private LocalDateTime requestedTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_position", nullable = false, length = 16)
    private RoutePosition routePosition;

    @Column(name = "has_packed_lunch", nullable = false)
    private boolean hasPackedLunch;

    @Column(name = "requires_parking", nullable = false)
    private boolean requiresParking;

    @Column(name = "escort_available", nullable = false)
    private boolean escortAvailable;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status;

    /** Snapshot of the decision that produced this booking; kept even if policies change later. */
    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 20)
    private Decision decision;

    @Enumerated(EnumType.STRING)
    @Column(name = "deciding_reason", length = 32)
    private ReasonCode decidingReason;

    /** JSON array of rule results from the original evaluation (explain snapshot). */
    @Column(name = "rule_results_json", columnDefinition = "text")
    private String ruleResultsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Booking() {
    }

    public Booking(Employee employee, LocalDate bookingDate, BookingDirection direction,
                   String pickupLocation, String dropLocation, LocalDateTime requestedTime,
                   RoutePosition routePosition, boolean hasPackedLunch, boolean requiresParking,
                   boolean escortAvailable, BookingStatus status, Decision decision,
                   ReasonCode decidingReason, String ruleResultsJson) {
        this.id = UUID.randomUUID();
        this.employee = employee;
        this.bookingDate = bookingDate;
        this.direction = direction;
        this.pickupLocation = pickupLocation;
        this.dropLocation = dropLocation;
        this.requestedTime = requestedTime;
        this.routePosition = routePosition;
        this.hasPackedLunch = hasPackedLunch;
        this.requiresParking = requiresParking;
        this.escortAvailable = escortAvailable;
        this.status = status;
        this.decision = decision;
        this.decidingReason = decidingReason;
        this.ruleResultsJson = ruleResultsJson;
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

    /** True when the booking still occupies its service slot (and possibly a seat). */
    public boolean isActive() {
        return status == BookingStatus.CONFIRMED || status == BookingStatus.PENDING_APPROVAL;
    }

    /** True when the booking currently consumes a reserved seat. */
    public boolean consumesSeat() {
        return status == BookingStatus.CONFIRMED;
    }

    public UUID getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public BookingDirection getDirection() {
        return direction;
    }

    public String getPickupLocation() {
        return pickupLocation;
    }

    public String getDropLocation() {
        return dropLocation;
    }

    public LocalDateTime getRequestedTime() {
        return requestedTime;
    }

    public RoutePosition getRoutePosition() {
        return routePosition;
    }

    public boolean isHasPackedLunch() {
        return hasPackedLunch;
    }

    public boolean isRequiresParking() {
        return requiresParking;
    }

    public boolean isEscortAvailable() {
        return escortAvailable;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Decision getDecision() {
        return decision;
    }

    public ReasonCode getDecidingReason() {
        return decidingReason;
    }

    public String getRuleResultsJson() {
        return ruleResultsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public void setDecision(Decision decision) {
        this.decision = decision;
    }

    public void setDecidingReason(ReasonCode decidingReason) {
        this.decidingReason = decidingReason;
    }

    public void setRuleResultsJson(String ruleResultsJson) {
        this.ruleResultsJson = ruleResultsJson;
    }
}
