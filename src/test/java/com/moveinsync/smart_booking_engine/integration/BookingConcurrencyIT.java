package com.moveinsync.smart_booking_engine.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.moveinsync.smart_booking_engine.dto.request.BookingRequest;
import com.moveinsync.smart_booking_engine.dto.response.BookingResponse;
import com.moveinsync.smart_booking_engine.dto.response.EvaluationResponse;
import com.moveinsync.smart_booking_engine.entity.Employee;
import com.moveinsync.smart_booking_engine.enums.BookingDirection;
import com.moveinsync.smart_booking_engine.enums.BookingStatus;
import com.moveinsync.smart_booking_engine.enums.Decision;
import com.moveinsync.smart_booking_engine.enums.Gender;
import com.moveinsync.smart_booking_engine.enums.ReasonCode;
import com.moveinsync.smart_booking_engine.enums.RoutePosition;
import com.moveinsync.smart_booking_engine.exception.EvaluationRejectedException;
import com.moveinsync.smart_booking_engine.repository.BookingRepository;
import com.moveinsync.smart_booking_engine.repository.CostCentreQuotaRepository;
import com.moveinsync.smart_booking_engine.service.BookingService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Real-PostgreSQL concurrency proof: last-seat race, duplicate race,
 * cancellation release and rollback. Uses the conditional UPDATE + partial
 * unique index, not mocks.
 */
class BookingConcurrencyIT extends PostgresIntegrationTest {

    @Autowired
    TestDataFactory factory;

    @Autowired
    BookingService bookingService;

    @Autowired
    BookingRepository bookingRepository;

    @Autowired
    CostCentreQuotaRepository quotaRepository;

    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 10, 15);
    private static final LocalDateTime SERVICE_TIME = LocalDateTime.of(2026, 10, 15, 10, 0);

    @BeforeEach
    void clean() {
        bookingRepository.deleteAll();
        factory.users.deleteAll();
        quotaRepository.deleteAll();
        factory.employees.deleteAll();
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Employee employee) {
        var principal = new com.moveinsync.smart_booking_engine.security.CustomUserDetails(
                UUID.randomUUID(), "test-user", "hash", "EMPLOYEE", employee.getId(), true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));
    }

    private BookingRequest requestFor(Employee employee) {
        return new BookingRequest(employee.getId(), SERVICE_DATE, BookingDirection.LOGIN,
                "Koramangala", "Whitefield", SERVICE_TIME, RoutePosition.OTHER,
                false, false, false);
    }

    private Employee dayShiftEmployee(String code, String costCentre) {
        return factory.employee(code, costCentre, Gender.MAN,
                LocalTime.of(9, 0), LocalTime.of(18, 0));
    }

    /** Capacity 1, two employees race: exactly one CONFIRMED, the other QUOTA_EXCEEDED. */
    @Test
    void concurrentLastSeatRaceReservesExactlyOnce() throws Exception {
        String costCentre = "CC-RACE-" + UUID.randomUUID().toString().substring(0, 8);
        factory.quota(costCentre, 1);
        Employee first = dayShiftEmployee("RACE-1-" + UUID.randomUUID().toString().substring(0, 8), costCentre);
        Employee second = dayShiftEmployee("RACE-2-" + UUID.randomUUID().toString().substring(0, 8), costCentre);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<BookingResponse> attempt1 = pool.submit(() -> {
                authenticateAs(first);
                ready.countDown();
                go.await(30, TimeUnit.SECONDS);
                return bookingService.create(requestFor(first));
            });
            Future<BookingResponse> attempt2 = pool.submit(() -> {
                authenticateAs(second);
                ready.countDown();
                go.await(30, TimeUnit.SECONDS);
                return bookingService.create(requestFor(second));
            });
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<BookingResponse> confirmed = new ArrayList<>();
            List<Throwable> rejected = new ArrayList<>();
            for (Future<BookingResponse> attempt : List.of(attempt1, attempt2)) {
                try {
                    confirmed.add(attempt.get(60, TimeUnit.SECONDS));
                } catch (java.util.concurrent.ExecutionException e) {
                    rejected.add(e.getCause());
                }
            }

            assertThat(confirmed).hasSize(1);
            assertThat(confirmed.get(0).status()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(rejected).hasSize(1);
            assertThat(rejected.get(0)).isInstanceOf(EvaluationRejectedException.class);
            EvaluationResponse evaluation =
                    ((EvaluationRejectedException) rejected.get(0)).getEvaluation();
            assertThat(evaluation.decision()).isEqualTo(Decision.DENY);
            assertThat(evaluation.failureReasons()).contains(ReasonCode.QUOTA_EXCEEDED);

            var quota = quotaRepository.findByCostCentreCode(costCentre).orElseThrow();
            assertThat(quota.getReservedSeats()).isEqualTo(1);
            assertThat(bookingRepository.findAll()).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    /** Same employee double-submits: one booking survives, the other is DUPLICATE. */
    @Test
    void concurrentDuplicateSubmissionsCreateOnlyOneBooking() throws Exception {
        String costCentre = "CC-DUP-" + UUID.randomUUID().toString().substring(0, 8);
        factory.quota(costCentre, 10);
        Employee employee = dayShiftEmployee("DUP-1-" + UUID.randomUUID().toString().substring(0, 8), costCentre);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<BookingResponse> attempt1 = pool.submit(() -> {
                authenticateAs(employee);
                ready.countDown();
                go.await(30, TimeUnit.SECONDS);
                return bookingService.create(requestFor(employee));
            });
            Future<BookingResponse> attempt2 = pool.submit(() -> {
                authenticateAs(employee);
                ready.countDown();
                go.await(30, TimeUnit.SECONDS);
                return bookingService.create(requestFor(employee));
            });
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            int confirmed = 0;
            int rejected = 0;
            for (Future<BookingResponse> attempt : List.of(attempt1, attempt2)) {
                try {
                    BookingResponse response = attempt.get(60, TimeUnit.SECONDS);
                    assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
                    confirmed++;
                } catch (java.util.concurrent.ExecutionException e) {
                    rejected++;
                }
            }
            assertThat(confirmed).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
            assertThat(bookingRepository.findAll()).hasSize(1);
            var quota = quotaRepository.findByCostCentreCode(costCentre).orElseThrow();
            assertThat(quota.getReservedSeats()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    /** Cancel frees exactly one seat; rebooking succeeds; double-cancel is harmless. */
    @Test
    void cancellationReleasesSeatExactlyOnce() {
        String costCentre = "CC-CANCEL-" + UUID.randomUUID().toString().substring(0, 8);
        factory.quota(costCentre, 1);
        Employee employee = dayShiftEmployee("CANCEL-1-" + UUID.randomUUID().toString().substring(0, 8), costCentre);
        authenticateAs(employee);

        BookingResponse created = bookingService.create(requestFor(employee));
        assertThat(quotaRepository.findByCostCentreCode(costCentre).orElseThrow()
                .getReservedSeats()).isEqualTo(1);

        BookingResponse cancelled = bookingService.cancel(created.id());
        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(quotaRepository.findByCostCentreCode(costCentre).orElseThrow()
                .getReservedSeats()).isEqualTo(0);

        BookingResponse cancelledAgain = bookingService.cancel(created.id());
        assertThat(cancelledAgain.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(quotaRepository.findByCostCentreCode(costCentre).orElseThrow()
                .getReservedSeats()).isEqualTo(0);

        BookingResponse rebooked = bookingService.create(requestFor(employee));
        assertThat(rebooked.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(quotaRepository.findByCostCentreCode(costCentre).orElseThrow()
                .getReservedSeats()).isEqualTo(1);
    }

    /** Concurrent cancellations of the same booking never drive quota negative. */
    @Test
    void concurrentCancellationsNeverDoubleRelease() throws Exception {
        String costCentre = "CC-CCANCEL-" + UUID.randomUUID().toString().substring(0, 8);
        factory.quota(costCentre, 1);
        Employee employee = dayShiftEmployee("CCANCEL-1-" + UUID.randomUUID().toString().substring(0, 8), costCentre);
        authenticateAs(employee);
        BookingResponse created = bookingService.create(requestFor(employee));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<BookingResponse> attempt1 = pool.submit(() -> {
                authenticateAs(employee);
                ready.countDown();
                go.await(30, TimeUnit.SECONDS);
                return bookingService.cancel(created.id());
            });
            Future<BookingResponse> attempt2 = pool.submit(() -> {
                authenticateAs(employee);
                ready.countDown();
                go.await(30, TimeUnit.SECONDS);
                return bookingService.cancel(created.id());
            });
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            assertThat(attempt1.get(60, TimeUnit.SECONDS).status())
                    .isEqualTo(BookingStatus.CANCELLED);
            assertThat(attempt2.get(60, TimeUnit.SECONDS).status())
                    .isEqualTo(BookingStatus.CANCELLED);
            assertThat(quotaRepository.findByCostCentreCode(costCentre).orElseThrow()
                    .getReservedSeats()).isEqualTo(0);
        } finally {
            pool.shutdownNow();
        }
    }

    /** Evaluation is advisory: it never reserves a seat. */
    @Test
    void evaluationDoesNotConsumeQuota() {
        String costCentre = "CC-EVAL-" + UUID.randomUUID().toString().substring(0, 8);
        factory.quota(costCentre, 1);
        Employee employee = dayShiftEmployee("EVAL-1-" + UUID.randomUUID().toString().substring(0, 8), costCentre);
        authenticateAs(employee);

        EvaluationResponse evaluation = bookingService.evaluate(requestFor(employee), true);
        assertThat(evaluation.decision()).isEqualTo(Decision.ALLOW);
        assertThat(quotaRepository.findByCostCentreCode(costCentre).orElseThrow()
                .getReservedSeats()).isEqualTo(0);
    }
}
