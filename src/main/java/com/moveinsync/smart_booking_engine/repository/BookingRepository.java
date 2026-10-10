package com.moveinsync.smart_booking_engine.repository;

import com.moveinsync.smart_booking_engine.entity.Booking;
import com.moveinsync.smart_booking_engine.enums.BookingDirection;
import com.moveinsync.smart_booking_engine.enums.BookingStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID>,
        JpaSpecificationExecutor<Booking> {

    /** Duplicate = same employee + service slot + direction among non-terminal bookings. */
    @Query("""
            select case when count(b) > 0 then true else false end
              from Booking b
             where b.employee.id = :employeeId
               and b.requestedTime = :requestedTime
               and b.direction = :direction
               and b.status in (com.moveinsync.smart_booking_engine.enums.BookingStatus.CONFIRMED,
                                com.moveinsync.smart_booking_engine.enums.BookingStatus.PENDING_APPROVAL)
               and (:excludeBookingId is null or b.id <> :excludeBookingId)
            """)
    boolean existsActiveForSlot(@Param("employeeId") UUID employeeId,
                                @Param("requestedTime") LocalDateTime requestedTime,
                                @Param("direction") BookingDirection direction,
                                @Param("excludeBookingId") UUID excludeBookingId);

    Optional<Booking> findByIdAndEmployeeId(UUID id, UUID employeeId);

    /** Row lock for cancel/approve: concurrent attempts serialize instead of version-clashing. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select b from Booking b
             where (:employeeId is null or b.employee.id = :employeeId)
               and (:date is null or b.bookingDate = :date)
               and (:status is null or b.status = :status)
            """)
    List<Booking> search(@Param("employeeId") UUID employeeId,
                         @Param("date") java.time.LocalDate date,
                         @Param("status") BookingStatus status);

    List<Booking> findByStatus(BookingStatus status);
}
