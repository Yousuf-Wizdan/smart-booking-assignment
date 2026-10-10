package com.moveinsync.smart_booking_engine.service;

import com.moveinsync.smart_booking_engine.dto.request.PolicyRuleCreateRequest;
import com.moveinsync.smart_booking_engine.dto.request.PolicyRuleUpdateRequest;
import com.moveinsync.smart_booking_engine.dto.response.PolicyRuleResponse;
import com.moveinsync.smart_booking_engine.entity.PolicyRule;
import com.moveinsync.smart_booking_engine.enums.RuleType;
import com.moveinsync.smart_booking_engine.exception.BadRequestException;
import com.moveinsync.smart_booking_engine.exception.ResourceNotFoundException;
import com.moveinsync.smart_booking_engine.repository.PolicyRuleRepository;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Policy rule CRUD. Every change is persisted and read fresh on the next
 * evaluation, so it takes effect without restart. Type-specific parameters
 * are validated here; invalid parameters fail the rule closed at evaluation
 * time as {@code INVALID_RULE_PARAMETERS}.
 */
@Service
public class PolicyService {

    private final PolicyRuleRepository policyRuleRepository;
    private final ObjectMapper objectMapper;

    public PolicyService(PolicyRuleRepository policyRuleRepository, ObjectMapper objectMapper) {
        this.policyRuleRepository = policyRuleRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PolicyRuleResponse> list() {
        return policyRuleRepository.findAllOrdered().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PolicyRuleResponse get(UUID id) {
        return toResponse(findById(id));
    }

    @Transactional
    public PolicyRuleResponse create(PolicyRuleCreateRequest request) {
        String parametersJson = normalizeParameters(request.ruleType(), request.parameters());
        PolicyRule rule = new PolicyRule(blankToNull(request.businessUnit()),
                blankToNull(request.costCentreCode()), request.ruleType(),
                request.enabled() == null || request.enabled(), request.priority(),
                request.hardDeny() == null || request.hardDeny(), parametersJson);
        return toResponse(policyRuleRepository.save(rule));
    }

    @Transactional
    public PolicyRuleResponse update(UUID id, PolicyRuleUpdateRequest request) {
        PolicyRule rule = findById(id);
        String parametersJson = normalizeParameters(request.ruleType(), request.parameters());
        rule.setBusinessUnit(blankToNull(request.businessUnit()));
        rule.setCostCentreCode(blankToNull(request.costCentreCode()));
        rule.setRuleType(request.ruleType());
        rule.setEnabled(request.enabled() == null || request.enabled());
        rule.setPriority(request.priority());
        rule.setHardDeny(request.hardDeny() == null || request.hardDeny());
        rule.setParametersJson(parametersJson);
        return toResponse(policyRuleRepository.save(rule));
    }

    @Transactional
    public PolicyRuleResponse setEnabled(UUID id, boolean enabled) {
        PolicyRule rule = findById(id);
        rule.setEnabled(enabled);
        return toResponse(policyRuleRepository.save(rule));
    }

    @Transactional
    public PolicyRuleResponse setPriority(UUID id, int priority) {
        PolicyRule rule = findById(id);
        rule.setPriority(priority);
        return toResponse(policyRuleRepository.save(rule));
    }

    @Transactional
    public void delete(UUID id) {
        policyRuleRepository.delete(findById(id));
    }

    private PolicyRule findById(UUID id) {
        return policyRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Policy rule not found: " + id));
    }

    private PolicyRuleResponse toResponse(PolicyRule rule) {
        return PolicyRuleResponse.from(rule, parametersAsMap(rule.getParametersJson()));
    }

    /**
     * Validates type-specific parameters and returns them as canonical JSON.
     * Parameterless rule types must carry an empty object.
     */
    String normalizeParameters(RuleType ruleType, Map<String, Object> parameters) {
        Map<String, Object> params = parameters == null ? Map.of() : parameters;
        try {
            return switch (ruleType) {
                case SHIFT_WINDOW, DUPLICATE_BOOKING, COST_CENTRE_QUOTA -> {
                    if (!params.isEmpty()) {
                        throw new BadRequestException(
                                ruleType + " takes no parameters; got: " + params.keySet());
                    }
                    yield "{}";
                }
                case BOOKING_CUTOFF -> {
                    Object value = params.get("cutoffMinutes");
                    if (!(value instanceof Number number) || number.longValue() < 0
                            || number.longValue() != number.doubleValue()) {
                        throw new BadRequestException(
                                "BOOKING_CUTOFF requires a non-negative integer 'cutoffMinutes'");
                    }
                    yield objectMapper.writeValueAsString(
                            Map.of("cutoffMinutes", number.longValue()));
                }
                case NIGHT_SAFETY -> {
                    Map<String, Object> normalized = new LinkedHashMap<>();
                    normalized.put("nightStart", parseTimeParam(params.get("nightStart"), "nightStart"));
                    normalized.put("nightEnd", parseTimeParam(params.get("nightEnd"), "nightEnd"));
                    Object escort = params.get("requireEscort");
                    if (!(escort instanceof Boolean)) {
                        throw new BadRequestException(
                                "NIGHT_SAFETY requires a boolean 'requireEscort'");
                    }
                    normalized.put("requireEscort", escort);
                    yield objectMapper.writeValueAsString(normalized);
                }
            };
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Invalid rule parameters: " + e.getMessage());
        }
    }

    private String parseTimeParam(Object value, String name) {
        if (!(value instanceof String text)) {
            throw new BadRequestException("NIGHT_SAFETY requires '" + name + "' as HH:mm");
        }
        try {
            return LocalTime.parse(text).toString();
        } catch (Exception e) {
            throw new BadRequestException("NIGHT_SAFETY requires '" + name + "' as HH:mm");
        }
    }

    private Map<String, Object> parametersAsMap(String parametersJson) {
        try {
            return objectMapper.readValue(parametersJson,
                    new tools.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            return Map.of("raw", parametersJson);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
