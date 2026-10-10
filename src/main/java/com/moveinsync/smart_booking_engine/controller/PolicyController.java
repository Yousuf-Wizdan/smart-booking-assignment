package com.moveinsync.smart_booking_engine.controller;

import com.moveinsync.smart_booking_engine.dto.request.PolicyEnabledRequest;
import com.moveinsync.smart_booking_engine.dto.request.PolicyPriorityRequest;
import com.moveinsync.smart_booking_engine.dto.request.PolicyRuleCreateRequest;
import com.moveinsync.smart_booking_engine.dto.request.PolicyRuleUpdateRequest;
import com.moveinsync.smart_booking_engine.dto.response.PolicyRuleResponse;
import com.moveinsync.smart_booking_engine.service.PolicyService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    public ResponseEntity<List<PolicyRuleResponse>> list() {
        return ResponseEntity.ok(policyService.list());
    }

    @PostMapping
    public ResponseEntity<PolicyRuleResponse> create(
            @Valid @RequestBody PolicyRuleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(policyService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PolicyRuleResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(policyService.get(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PolicyRuleResponse> update(@PathVariable UUID id,
            @Valid @RequestBody PolicyRuleUpdateRequest request) {
        return ResponseEntity.ok(policyService.update(id, request));
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<PolicyRuleResponse> setEnabled(@PathVariable UUID id,
            @Valid @RequestBody PolicyEnabledRequest request) {
        return ResponseEntity.ok(policyService.setEnabled(id, request.enabled()));
    }

    @PatchMapping("/{id}/priority")
    public ResponseEntity<PolicyRuleResponse> setPriority(@PathVariable UUID id,
            @Valid @RequestBody PolicyPriorityRequest request) {
        return ResponseEntity.ok(policyService.setPriority(id, request.priority()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        policyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
