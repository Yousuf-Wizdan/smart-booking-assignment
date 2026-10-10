package com.moveinsync.smart_booking_engine.controller;

import com.moveinsync.smart_booking_engine.dto.response.BookingResponse;
import com.moveinsync.smart_booking_engine.service.ApprovalService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/pending")
    public ResponseEntity<Page<BookingResponse>> pending(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(approvalService.pending(pageable));
    }

    @PostMapping("/{bookingId}/approve")
    public ResponseEntity<BookingResponse> approve(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(approvalService.approve(bookingId));
    }

    @PostMapping("/{bookingId}/reject")
    public ResponseEntity<BookingResponse> reject(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(approvalService.reject(bookingId));
    }
}
