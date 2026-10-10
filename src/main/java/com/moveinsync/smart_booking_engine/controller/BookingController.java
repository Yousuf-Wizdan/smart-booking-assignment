package com.moveinsync.smart_booking_engine.controller;

import com.moveinsync.smart_booking_engine.dto.request.BookingRequest;
import com.moveinsync.smart_booking_engine.dto.response.BookingResponse;
import com.moveinsync.smart_booking_engine.dto.response.EvaluationResponse;
import com.moveinsync.smart_booking_engine.dto.response.ExplanationResponse;
import com.moveinsync.smart_booking_engine.enums.BookingStatus;
import com.moveinsync.smart_booking_engine.service.BookingService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<EvaluationResponse> evaluate(
            @Valid @RequestBody BookingRequest request,
            @RequestParam(name = "explain", defaultValue = "true") boolean explain) {
        return ResponseEntity.ok(bookingService.evaluate(request, explain));
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(bookingService.get(id));
    }

    @GetMapping
    public ResponseEntity<Page<BookingResponse>> list(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(bookingService.list(employeeId, date, status, pageable));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(bookingService.cancel(id));
    }

    @GetMapping("/{id}/explanation")
    public ResponseEntity<ExplanationResponse> explanation(@PathVariable UUID id) {
        return ResponseEntity.ok(bookingService.explain(id));
    }
}
