package com.moveinsync.smart_booking_engine.service;

import com.moveinsync.smart_booking_engine.dto.request.BookingRequest;
import com.moveinsync.smart_booking_engine.dto.response.BookingResponse;
import com.moveinsync.smart_booking_engine.dto.response.EvaluationResponse;
import com.moveinsync.smart_booking_engine.dto.response.ExplanationResponse;
import com.moveinsync.smart_booking_engine.dto.response.RuleResultResponse;
import com.moveinsync.smart_booking_engine.entity.Booking;
import com.moveinsync.smart_booking_engine.entity.Employee;
import com.moveinsync.smart_booking_engine.enums.BookingStatus;
import com.moveinsync.smart_booking_engine.enums.Decision;
import com.moveinsync.smart_booking_engine.enums.ReasonCode;
import com.moveinsync.smart_booking_engine.exception.BadRequestException;
import com.moveinsync.smart_booking_engine.exception.ConflictException;
import com.moveinsync.smart_booking_engine.exception.EvaluationRejectedException;
import com.moveinsync.smart_booking_engine.exception.ForbiddenException;
import com.moveinsync.smart_booking_engine.exception.ResourceNotFoundException;
import com.moveinsync.smart_booking_engine.repository.BookingRepository;
import com.moveinsync.smart_booking_engine.repository.CostCentreQuotaRepository;
import com.moveinsync.smart_booking_engine.repository.EmployeeRepository;
import com.moveinsync.smart_booking_engine.security.AuthenticatedUser;
import com.moveinsync.smart_booking_engine.security.CustomUserDetails;
import com.moveinsync.smart_booking_engine.service.rule.EngineResult;
import com.moveinsync.smart_booking_engine.service.rule.RuleContext;
import com.moveinsync.smart_booking_engine.service.rule.RuleEngine;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Booking evaluation (advisory, never touches quota) and booking creation
 * (transactional: re-evaluate, atomically reserve one seat, persist).
 *
 * <p>Quota safety: the seat is reserved with a conditional UPDATE
 * ({@code reserved_seats < capacity}) in the same transaction as the booking
 * insert, so concurrent last-seat requests cannot oversell. A failed insert
 * (duplicate race) rolls the reservation back.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final EmployeeRepository employeeRepository;
    private final CostCentreQuotaRepository quotaRepository;
    private final RuleEngine ruleEngine;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ZoneId companyZone;
    private final MeterRegistry meterRegistry;

    public BookingService(BookingRepository bookingRepository, EmployeeRepository employeeRepository,
                          CostCentreQuotaRepository quotaRepository, RuleEngine ruleEngine,
                          ObjectMapper objectMapper, Clock clock, ZoneId companyZone,
                          MeterRegistry meterRegistry) {
        this.bookingRepository = bookingRepository;
        this.employeeRepository = employeeRepository;
        this.quotaRepository = quotaRepository;
        this.ruleEngine = ruleEngine;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.companyZone = companyZone;
        this.meterRegistry = meterRegistry;
    }

    /** Advisory evaluation only: no quota or booking side effects. */
    @Transactional(readOnly = true)
    public EvaluationResponse evaluate(BookingRequest request, boolean explain) {
        Employee employee = resolveTargetEmployee(request.employeeId());
        validateRequest(request);
        EvaluationResponse response = evaluateFor(employee, request, null, explain);
        log.info("booking evaluation requestId={} employee={} decision={} reasons={}",
                response.requestId(), employee.getEmployeeCode(), response.decision(),
                response.failureReasons());
        return response;
    }

    /**
     * Evaluate, then persist: ALLOW becomes CONFIRMED (seat reserved atomically),
     * NEEDS_APPROVAL becomes PENDING_APPROVAL (no seat consumed), DENY is
     * rejected with the evaluation as the 409 body.
     */
    @Transactional
    public BookingResponse create(BookingRequest request) {
        Employee employee = resolveTargetEmployee(request.employeeId());
        validateRequest(request);
        if (!employee.isActive()) {
            throw new BadRequestException("Employee is inactive: " + employee.getEmployeeCode());
        }
        EvaluationResponse evaluation = evaluateFor(employee, request, null, true);

        if (evaluation.decision() == Decision.DENY) {
            throw new EvaluationRejectedException(
                    "Booking denied: " + evaluation.decidingReason(), evaluation);
        }
        if (evaluation.decision() == Decision.NEEDS_APPROVAL) {
            Booking pending = persist(request, employee, evaluation, BookingStatus.PENDING_APPROVAL);
            meterRegistry.counter("booking.created", "status", "PENDING_APPROVAL").increment();
            log.info("booking pending approval id={} employee={} reasons={}",
                    pending.getId(), employee.getEmployeeCode(), evaluation.failureReasons());
            return BookingResponse.from(pending);
        }

        int reserved = quotaRepository.reserveSeat(employee.getCostCentreCode());
        if (reserved == 0) {
            EvaluationResponse fresh = evaluateFor(employee, request, null, true);
            throw new EvaluationRejectedException(
                    "No seats left for cost centre " + employee.getCostCentreCode()
                            + " (QUOTA_EXCEEDED)",
                    new EvaluationResponse(Decision.DENY, ReasonCode.QUOTA_EXCEEDED,
                            List.of(ReasonCode.QUOTA_EXCEEDED), fresh.ruleResults(),
                            Instant.now(clock), fresh.requestId()));
        }
        try {
            Booking confirmed = persist(request, employee, evaluation, BookingStatus.CONFIRMED);
            bookingRepository.flush();
            meterRegistry.counter("booking.created", "status", "CONFIRMED").increment();
            log.info("booking confirmed id={} employee={} costCentre={}", confirmed.getId(),
                    employee.getEmployeeCode(), employee.getCostCentreCode());
            return BookingResponse.from(confirmed);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    "Duplicate booking for this employee, service slot and direction (DUPLICATE)");
        }
    }

    /**
     * Cancel an active booking. Idempotent: cancelling an already-cancelled
     * booking returns it unchanged without releasing another seat.
     */
    @Transactional
    public BookingResponse cancel(UUID bookingId) {
        CustomUserDetails user = AuthenticatedUser.require();
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
        if (!user.isAdmin() && !booking.getEmployee().getId().equals(user.getEmployeeId())) {
            throw new ForbiddenException("Employees may only cancel their own bookings");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return BookingResponse.from(booking);
        }
        if (booking.getStatus() == BookingStatus.REJECTED) {
            throw new ConflictException("Booking was already rejected and cannot be cancelled");
        }
        boolean heldSeat = booking.consumesSeat();
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.saveAndFlush(booking);
        if (heldSeat) {
            quotaRepository.releaseSeat(booking.getEmployee().getCostCentreCode());
            meterRegistry.counter("booking.cancelled", "releasedSeat", "true").increment();
            log.info("booking cancelled id={} seatReleased=true", booking.getId());
        } else {
            meterRegistry.counter("booking.cancelled", "releasedSeat", "false").increment();
            log.info("booking cancelled id={} seatReleased=false (pending approval held no seat)",
                    booking.getId());
        }
        return BookingResponse.from(booking);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(UUID bookingId) {
        return BookingResponse.from(findVisible(bookingId));
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> list(UUID employeeId, java.time.LocalDate date,
                                      BookingStatus status, Pageable pageable) {
        CustomUserDetails user = AuthenticatedUser.require();
        UUID effectiveEmployeeId = employeeId;
        if (!user.isAdmin() && !isApprover(user)) {
            if (employeeId != null && !employeeId.equals(user.getEmployeeId())) {
                throw new ForbiddenException("Employees may only list their own bookings");
            }
            effectiveEmployeeId = user.getEmployeeId();
        }
        return bookingRepository
                .findAll(org.springframework.data.jpa.domain.Specification
                        .where(BookingSpecifications.employeeId(effectiveEmployeeId))
                        .and(BookingSpecifications.date(date))
                        .and(BookingSpecifications.status(status)), pageable)
                .map(BookingResponse::from);
    }

    /** The stored decision snapshot: explains the original decision after policies change. */
    @Transactional(readOnly = true)
    public ExplanationResponse explain(UUID bookingId) {
        Booking booking = findVisible(bookingId);
        List<RuleResultResponse> ruleResults = readSnapshot(booking);
        LinkedHashSet<ReasonCode> failures = new LinkedHashSet<>();
        for (RuleResultResponse result : ruleResults) {
            if (result.outcome() == com.moveinsync.smart_booking_engine.enums.RuleOutcome.FAIL
                    && result.reasonCode() != null) {
                failures.add(result.reasonCode());
            }
        }
        return new ExplanationResponse(booking.getId(), booking.getDecision(),
                booking.getDecidingReason(), List.copyOf(failures), ruleResults,
                booking.getUpdatedAt());
    }

    private Booking findVisible(UUID bookingId) {
        CustomUserDetails user = AuthenticatedUser.require();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
        if (user.isAdmin() || isApprover(user)) {
            return booking;
        }
        if (!booking.getEmployee().getId().equals(user.getEmployeeId())) {
            throw new ForbiddenException("Employees may only read their own bookings");
        }
        return booking;
    }

    private boolean isApprover(CustomUserDetails user) {
        return "APPROVER".equals(user.getRole());
    }

    EvaluationResponse evaluateFor(Employee employee, BookingRequest request,
                                   UUID existingBookingId, boolean explain) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), companyZone);
        RuleContext context = new RuleContext(employee, request.requestedTime(),
                request.direction(), request.routePosition(),
                request.escortAvailable() != null && request.escortAvailable(), now,
                existingBookingId);
        EngineResult result = ruleEngine.evaluate(context);
        meterRegistry.counter("booking.evaluations", "decision",
                result.decision().name()).increment();
        for (ReasonCode reason : result.failureReasons()) {
            meterRegistry.counter("booking.rule_violations", "reason", reason.name()).increment();
        }
        List<RuleResultResponse> ruleResults = explain
                ? result.ruleResults().stream()
                        .map(r -> new RuleResultResponse(r.ruleType(), r.priority(), r.outcome(),
                                r.reasonCode(), r.message(), r.hardDeny(), r.details()))
                        .toList()
                : List.of();
        String requestId = MDC.get("requestId");
        return new EvaluationResponse(result.decision(), result.decidingReason(),
                result.failureReasons(), ruleResults, Instant.now(clock),
                requestId == null ? UUID.randomUUID().toString() : requestId);
    }

    String snapshotOf(EvaluationResponse evaluation) {
        try {
            return objectMapper.writeValueAsString(evaluation.ruleResults());
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize rule snapshot", e);
        }
    }

    private Booking persist(BookingRequest request, Employee employee,
                            EvaluationResponse evaluation, BookingStatus status) {
        Booking booking = new Booking(employee, request.bookingDate(), request.direction(),
                request.pickupLocation().trim(), request.dropLocation().trim(),
                request.requestedTime(), request.routePosition(),
                request.hasPackedLunch() != null && request.hasPackedLunch(),
                request.requiresParking() != null && request.requiresParking(),
                request.escortAvailable() != null && request.escortAvailable(), status,
                evaluation.decision(), evaluation.decidingReason(), snapshotOf(evaluation));
        return bookingRepository.save(booking);
    }

    private List<RuleResultResponse> readSnapshot(Booking booking) {
        String json = booking.getRuleResultsJson();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    new tools.jackson.core.type.TypeReference<List<RuleResultResponse>>() {
                    });
        } catch (Exception e) {
            throw new IllegalStateException("Stored rule snapshot is unreadable", e);
        }
    }

    /**
     * The server derives identity from the principal, never from request ids:
     * non-admin callers always act as their linked employee.
     */
    private Employee resolveTargetEmployee(UUID requestedId) {
        CustomUserDetails user = AuthenticatedUser.require();
        if (user.isAdmin()) {
            if (requestedId != null) {
                return findEmployee(requestedId);
            }
            if (user.getEmployeeId() != null) {
                return findEmployee(user.getEmployeeId());
            }
            throw new BadRequestException("employeeId is required");
        }
        if (user.getEmployeeId() == null) {
            throw new ForbiddenException("This account has no linked employee profile");
        }
        if (requestedId != null && !requestedId.equals(user.getEmployeeId())) {
            throw new ForbiddenException("Cannot evaluate or book for another employee");
        }
        return findEmployee(user.getEmployeeId());
    }

    private Employee findEmployee(UUID id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));
    }

    private void validateRequest(BookingRequest request) {
        String pickup = request.pickupLocation().trim();
        String drop = request.dropLocation().trim();
        if (pickup.equalsIgnoreCase(drop)) {
            throw new BadRequestException("Pickup and drop locations must differ");
        }
        if (!request.requestedTime().toLocalDate().equals(request.bookingDate())) {
            throw new BadRequestException(
                    "requestedTime must fall on bookingDate (both in " + companyZone + ")");
        }
    }
}
