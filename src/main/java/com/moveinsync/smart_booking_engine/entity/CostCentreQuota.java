package com.moveinsync.smart_booking_engine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Seat quota for one cost centre. Invariant: {@code 0 <= reservedSeats <= capacity}.
 * The invariant is enforced by conditional UPDATE statements (see
 * {@code CostCentreQuotaRepository}) plus a CHECK constraint in the schema.
 */
@Entity
@Table(name = "cost_centre_quota")
public class CostCentreQuota {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "cost_centre_code", nullable = false, unique = true, length = 32)
    private String costCentreCode;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "reserved_seats", nullable = false)
    private int reservedSeats;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CostCentreQuota() {
    }

    public CostCentreQuota(String costCentreCode, int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be non-negative");
        }
        this.id = UUID.randomUUID();
        this.costCentreCode = costCentreCode;
        this.capacity = capacity;
        this.reservedSeats = 0;
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

    public boolean hasFreeSeat() {
        return reservedSeats < capacity;
    }

    public UUID getId() {
        return id;
    }

    public String getCostCentreCode() {
        return costCentreCode;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getReservedSeats() {
        return reservedSeats;
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

    public void setCapacity(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be non-negative");
        }
        this.capacity = capacity;
    }

    /** Only used by admin corrections; booking flows use the atomic repository updates. */
    public void setReservedSeats(int reservedSeats) {
        if (reservedSeats < 0) {
            throw new IllegalArgumentException("reservedSeats must be non-negative");
        }
        this.reservedSeats = reservedSeats;
    }
}
