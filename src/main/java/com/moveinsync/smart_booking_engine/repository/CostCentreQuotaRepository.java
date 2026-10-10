package com.moveinsync.smart_booking_engine.repository;

import com.moveinsync.smart_booking_engine.entity.CostCentreQuota;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CostCentreQuotaRepository extends JpaRepository<CostCentreQuota, UUID> {

    Optional<CostCentreQuota> findByCostCentreCode(String costCentreCode);

    /**
     * Atomically reserve one seat. Returns 1 if a seat was reserved, 0 if the quota
     * is full or missing. The conditional update (not read-then-write) is what makes
     * concurrent last-seat requests safe.
     */
    @Modifying
    @Query(value = """
            update cost_centre_quota
               set reserved_seats = reserved_seats + 1,
                   updated_at = now()
             where cost_centre_code = :costCentreCode
               and reserved_seats < capacity
            """, nativeQuery = true)
    int reserveSeat(@Param("costCentreCode") String costCentreCode);

    /**
     * Atomically release one seat. Returns 1 if a seat was released, 0 if there was
     * nothing to release (missing quota or already zero). Guarding on
     * {@code reserved_seats > 0} keeps the 0 <= reservedSeats <= capacity invariant
     * and makes double-cancellation harmless.
     */
    @Modifying
    @Query(value = """
            update cost_centre_quota
               set reserved_seats = reserved_seats - 1,
                   updated_at = now()
             where cost_centre_code = :costCentreCode
               and reserved_seats > 0
            """, nativeQuery = true)
    int releaseSeat(@Param("costCentreCode") String costCentreCode);
}
