package com.moveinsync.smart_booking_engine.service;

import com.moveinsync.smart_booking_engine.dto.request.BookingRequest;
import com.moveinsync.smart_booking_engine.dto.response.BookingResponse;
import com.moveinsync.smart_booking_engine.dto.response.EvaluationResponse;
import com.moveinsync.smart_booking_engine.entity.Booking;
import com.moveinsync.smart_booking_engine.enums.BookingStatus;
import com.moveinsync.smart_booking_engine.enums.Decision;
import com.moveinsync.smart_booking_engine.exception.ConflictException;
import com.moveinsync.smart_booking_engine.exception.EvaluationRejectedException;
import com.moveinsync.smart_booking_engine.exception.ResourceNotFoundException;
import com.moveinsync.smart_booking_engine.repository.BookingRepository;
import com.moveinsync.smart_booking_engine.repository.CostCentreQuotaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Human review for bookings the rules could not auto-decide. Approving
 * re-evaluates the current rules (policies may have changed), then atomically
 * reserves one seat before confirming; if the seat is gone or a hard-deny now
 * applies, the booking is not confirmed.
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private final BookingRepository bookingRepository;
    private final CostCentreQuotaRepository quotaRepository;
    private final BookingService bookingService;
    private final MeterRegistry meterRegistry;

    public ApprovalService(BookingRepository bookingRepository,
                           CostCentreQuotaRepository quotaRepository,
                           BookingService bookingService, MeterRegistry meterRegistry) {
        this.bookingRepository = bookingRepository;
        this.quotaRepository = quotaRepository;
        this.bookingService = bookingService;
        this.meterRegistry = meterRegistry;
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> pending(Pageable pageable) {
        return bookingRepository
                .findAll((root, query, criteria) -> criteria.equal(root.get("status"),
                        BookingStatus.PENDING_APPROVAL), pageable)
                .map(BookingResponse::from);
    }

    @Transactional
    public BookingResponse approve(UUID bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
        if (booking.getStatus() != BookingStatus.PENDING_APPROVAL) {
            throw new ConflictException("Only pending-approval bookings can be approved"
                    + " (current status: " + booking.getStatus() + ")");
        }
        EvaluationResponse evaluation = bookingService.evaluateFor(booking.getEmployee(),
                toRequest(booking), booking.getId(), true);
        if (evaluation.decision() == Decision.DENY) {
            throw new EvaluationRejectedException(
                    "Approval blocked: current policies deny this booking ("
                            + evaluation.decidingReason() + ")",
                    evaluation);
        }
        int reserved = quotaRepository.reserveSeat(booking.getEmployee().getCostCentreCode());
        if (reserved == 0) {
            throw new ConflictException("No seats left for cost centre "
                    + booking.getEmployee().getCostCentreCode() + " (QUOTA_EXCEEDED)");
        }
        try {
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setDecision(evaluation.decision());
            booking.setDecidingReason(evaluation.decidingReason());
            booking.setRuleResultsJson(bookingService.snapshotOf(evaluation));
            bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Approving this booking conflicts with the current state");
        }
        meterRegistry.counter("booking.approvals", "outcome", "approved").increment();
        log.info("booking approved id={} employee={}", booking.getId(),
                booking.getEmployee().getEmployeeCode());
        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse reject(UUID bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
        if (booking.getStatus() != BookingStatus.PENDING_APPROVAL) {
            throw new ConflictException("Only pending-approval bookings can be rejected"
                    + " (current status: " + booking.getStatus() + ")");
        }
        booking.setStatus(BookingStatus.REJECTED);
        bookingRepository.save(booking);
        meterRegistry.counter("booking.approvals", "outcome", "rejected").increment();
        log.info("booking rejected id={} employee={}", booking.getId(),
                booking.getEmployee().getEmployeeCode());
        return BookingResponse.from(booking);
    }

    private BookingRequest toRequest(Booking booking) {
        return new BookingRequest(booking.getEmployee().getId(), booking.getBookingDate(),
                booking.getDirection(), booking.getPickupLocation(), booking.getDropLocation(),
                booking.getRequestedTime(), booking.getRoutePosition(),
                booking.isHasPackedLunch(), booking.isRequiresParking(),
                booking.isEscortAvailable());
    }

    /** Used by tests to seed PENDING_APPROVAL bookings deterministically. */
    List<Booking> pendingApprovals() {
        return bookingRepository.findByStatus(BookingStatus.PENDING_APPROVAL);
    }
}
