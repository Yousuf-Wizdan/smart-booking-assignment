package com.moveinsync.smart_booking_engine.controller;

import com.moveinsync.smart_booking_engine.dto.request.QuotaCreateRequest;
import com.moveinsync.smart_booking_engine.dto.request.QuotaUpdateRequest;
import com.moveinsync.smart_booking_engine.dto.response.QuotaResponse;
import com.moveinsync.smart_booking_engine.service.QuotaService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quotas")
public class QuotaController {

    private final QuotaService quotaService;

    public QuotaController(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @GetMapping
    public ResponseEntity<List<QuotaResponse>> list() {
        return ResponseEntity.ok(quotaService.list());
    }

    @PostMapping
    public ResponseEntity<QuotaResponse> create(@Valid @RequestBody QuotaCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(quotaService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<QuotaResponse> update(@PathVariable UUID id,
            @Valid @RequestBody QuotaUpdateRequest request) {
        return ResponseEntity.ok(quotaService.update(id, request));
    }
}
