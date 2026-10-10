package com.moveinsync.smart_booking_engine.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moveinsync.smart_booking_engine.dto.request.BookingRequest;
import com.moveinsync.smart_booking_engine.entity.Employee;
import com.moveinsync.smart_booking_engine.enums.BookingDirection;
import com.moveinsync.smart_booking_engine.enums.Gender;
import com.moveinsync.smart_booking_engine.enums.Role;
import com.moveinsync.smart_booking_engine.enums.RoutePosition;
import com.moveinsync.smart_booking_engine.repository.BookingRepository;
import com.moveinsync.smart_booking_engine.repository.CostCentreQuotaRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/**
 * HTTP-level proof: auth, role enforcement, ownership, rule reason codes,
 * policy changes taking effect without restart, and the approval flow.
 */
class BookingFlowIT extends PostgresIntegrationTest {

    @Autowired
    WebApplicationContext webApplicationContext;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    TestDataFactory factory;

    @Autowired
    BookingRepository bookingRepository;

    @Autowired
    CostCentreQuotaRepository quotaRepository;

    @Autowired
    com.moveinsync.smart_booking_engine.repository.PolicyRuleRepository policyRuleRepository;

    private MockMvc mockMvc;
    private String costCentre;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        SecurityContextHolder.clearContext();
        bookingRepository.deleteAll();
        factory.users.deleteAll();
        quotaRepository.deleteAll();
        factory.employees.deleteAll();
        resetPoliciesToSeeds();
        costCentre = "CC-FLOW-" + UUID.randomUUID().toString().substring(0, 8);
        factory.quota(costCentre, 10);
    }

    /** Policies are global rows: every test starts from the V2 seed set. */
    private void resetPoliciesToSeeds() {
        policyRuleRepository.deleteAll();
        policyRuleRepository.save(new com.moveinsync.smart_booking_engine.entity.PolicyRule(
                null, null, com.moveinsync.smart_booking_engine.enums.RuleType.SHIFT_WINDOW,
                true, 10, true, "{}"));
        policyRuleRepository.save(new com.moveinsync.smart_booking_engine.entity.PolicyRule(
                null, null, com.moveinsync.smart_booking_engine.enums.RuleType.NIGHT_SAFETY,
                true, 20, true,
                "{\"nightStart\":\"22:00\",\"nightEnd\":\"05:00\",\"requireEscort\":true}"));
        policyRuleRepository.save(new com.moveinsync.smart_booking_engine.entity.PolicyRule(
                null, null, com.moveinsync.smart_booking_engine.enums.RuleType.BOOKING_CUTOFF,
                true, 30, true, "{\"cutoffMinutes\":60}"));
        policyRuleRepository.save(new com.moveinsync.smart_booking_engine.entity.PolicyRule(
                null, null, com.moveinsync.smart_booking_engine.enums.RuleType.DUPLICATE_BOOKING,
                true, 40, true, "{}"));
        policyRuleRepository.save(new com.moveinsync.smart_booking_engine.entity.PolicyRule(
                null, null,
                com.moveinsync.smart_booking_engine.enums.RuleType.COST_CENTRE_QUOTA,
                true, 50, true, "{}"));
    }

    private Employee dayShiftEmployee(String code, Gender gender) {
        return factory.employee(code + "-" + UUID.randomUUID().toString().substring(0, 8), costCentre, gender,
                LocalTime.of(9, 0), LocalTime.of(18, 0));
    }

    private String bookingJson(Employee employee, LocalDateTime serviceTime,
                               RoutePosition position, boolean escort) throws Exception {
        BookingRequest request = new BookingRequest(employee.getId(), serviceTime.toLocalDate(),
                BookingDirection.LOGIN, "Koramangala", "Whitefield", serviceTime, position,
                false, false, escort);
        return objectMapper.writeValueAsString(request);
    }

    @Test
    void registrationAndLoginIssueTokens() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String body = """
                {"username":"newuser-%s","password":"password123","employeeCode":"NEW-%s",
                 "fullName":"New User","businessUnit":"BU-TEST","costCentreCode":"%s",
                 "gender":"MAN","shiftStart":"09:00","shiftEnd":"18:00"}
                """.formatted(suffix, suffix, costCentre);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));

        String username = objectMapper.readTree(
                mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.replace("newuser-", "other-").replace("NEW-", "OTHER-")))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()).get("username").asText();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void registrationRejectsDuplicateUsername() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String body = """
                {"username":"dupuser-%s","password":"password123","employeeCode":"DUP-%s",
                 "fullName":"Dup User","businessUnit":"BU-TEST","costCentreCode":"%s",
                 "gender":"MAN","shiftStart":"09:00","shiftEnd":"18:00"}
                """.formatted(suffix, suffix, costCentre);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/bookings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/policies")).andExpect(status().isUnauthorized());
    }

    @Test
    void validBookingEvaluatesAllowAndCreatesConfirmed() throws Exception {
        Employee employee = dayShiftEmployee("FLOW-ALLOW", Gender.MAN);
        String token = factory.employeeToken(employee);

        mockMvc.perform(post("/api/v1/bookings/evaluate")
                        .header("Authorization", factory.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(employee,
                                LocalDateTime.of(2026, 10, 15, 10, 0), RoutePosition.OTHER,
                                false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOW"))
                .andExpect(jsonPath("$.ruleResults").isArray())
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        var quotaBefore = quotaRepository.findByCostCentreCode(costCentre).orElseThrow();
        assertThat(quotaBefore.getReservedSeats()).isEqualTo(0);

        String created = mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", factory.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(employee,
                                LocalDateTime.of(2026, 10, 15, 10, 0), RoutePosition.OTHER,
                                false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.decision").value("ALLOW"))
                .andReturn().getResponse().getContentAsString();
        UUID bookingId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        assertThat(quotaRepository.findByCostCentreCode(costCentre).orElseThrow()
                .getReservedSeats()).isEqualTo(1);

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                        .header("Authorization", factory.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(quotaRepository.findByCostCentreCode(costCentre).orElseThrow()
                .getReservedSeats()).isEqualTo(0);
    }

    @Test
    void outOfShiftBookingIsDeniedWithReasonCode() throws Exception {
        Employee employee = dayShiftEmployee("FLOW-SHIFT", Gender.MAN);
        String token = factory.employeeToken(employee);

        mockMvc.perform(post("/api/v1/bookings/evaluate")
                        .header("Authorization", factory.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(employee,
                                LocalDateTime.of(2026, 10, 15, 22, 30), RoutePosition.OTHER,
                                false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.decidingReason").value("OUTSIDE_SHIFT_WINDOW"))
                .andExpect(jsonPath("$.failureReasons[0]").value("OUTSIDE_SHIFT_WINDOW"));
    }

    @Test
    void nightSafetyViolationIsHardDeny() throws Exception {
        Employee employee = factory.employee("NIGHT-" + UUID.randomUUID().toString().substring(0, 8), costCentre,
                Gender.WOMAN, LocalTime.of(20, 0), LocalTime.of(6, 0));
        String token = factory.employeeToken(employee);

        mockMvc.perform(post("/api/v1/bookings/evaluate")
                        .header("Authorization", factory.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(employee,
                                LocalDateTime.of(2026, 10, 15, 23, 0), RoutePosition.FIRST_PICKUP,
                                false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.failureReasons[0]").value("NIGHT_SAFETY"));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", factory.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(employee,
                                LocalDateTime.of(2026, 10, 15, 23, 0), RoutePosition.FIRST_PICKUP,
                                false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.failureReasons[0]").value("NIGHT_SAFETY"));
        assertThat(bookingRepository.findAll()).isEmpty();
    }

    @Test
    void pastCutoffBookingIsDenied() throws Exception {
        Employee employee = dayShiftEmployee("FLOW-CUTOFF", Gender.MAN);
        String token = factory.employeeToken(employee);

        mockMvc.perform(post("/api/v1/bookings/evaluate")
                        .header("Authorization", factory.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("explain", "false")
                        .content(bookingJson(employee,
                                LocalDateTime.of(2020, 1, 1, 10, 0), RoutePosition.OTHER,
                                false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.decidingReason").value("PAST_CUTOFF"))
                .andExpect(jsonPath("$.ruleResults").isEmpty());
    }

    @Test
    void employeeCannotActForAnotherEmployee() throws Exception {
        Employee mine = dayShiftEmployee("FLOW-MINE", Gender.MAN);
        Employee other = dayShiftEmployee("FLOW-OTHER", Gender.MAN);
        String token = factory.employeeToken(mine);

        BookingRequest otherRequest = new BookingRequest(other.getId(), LocalDate.of(2026, 10, 15),
                BookingDirection.LOGIN, "A", "B", LocalDateTime.of(2026, 10, 15, 10, 0),
                RoutePosition.OTHER, false, false, false);
        mockMvc.perform(post("/api/v1/bookings/evaluate")
                        .header("Authorization", factory.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otherRequest)))
                .andExpect(status().isForbidden());

        BookingResponseCreated created = createBookingAs(mine, token);
        String otherToken = factory.employeeToken(other);
        mockMvc.perform(get("/api/v1/bookings/" + created.id)
                        .header("Authorization", factory.bearer(otherToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/bookings/" + created.id + "/cancel")
                        .header("Authorization", factory.bearer(otherToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotManagePolicies() throws Exception {
        Employee employee = dayShiftEmployee("FLOW-POL", Gender.MAN);
        String token = factory.employeeToken(employee);
        mockMvc.perform(get("/api/v1/policies")
                        .header("Authorization", factory.bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/policies")
                        .header("Authorization", factory.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruleType\":\"SHIFT_WINDOW\",\"priority\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void approverCannotManagePoliciesButAdminCan() throws Exception {
        String approverToken = factory.approverToken("approver-" + UUID.randomUUID().toString().substring(0, 8));
        mockMvc.perform(get("/api/v1/policies")
                        .header("Authorization", factory.bearer(approverToken)))
                .andExpect(status().isForbidden());

        String adminToken = factory.adminToken("admin-" + UUID.randomUUID().toString().substring(0, 8));
        mockMvc.perform(get("/api/v1/policies")
                        .header("Authorization", factory.bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void policyChangeTakesEffectWithoutRestart() throws Exception {
        Employee employee = dayShiftEmployee("FLOW-POLICY", Gender.MAN);
        String token = factory.employeeToken(employee);
        String adminToken = factory.adminToken("admin-" + UUID.randomUUID().toString().substring(0, 8));

        String policies = mockMvc.perform(get("/api/v1/policies")
                        .header("Authorization", factory.bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String cutoffId = null;
        for (var node : objectMapper.readTree(policies)) {
            if ("BOOKING_CUTOFF".equals(node.get("ruleType").asText())) {
                cutoffId = node.get("id").asText();
            }
        }
        assertThat(cutoffId).isNotNull();

        mockMvc.perform(patch("/api/v1/policies/" + cutoffId + "/enabled")
                        .header("Authorization", factory.bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(post("/api/v1/bookings/evaluate")
                        .header("Authorization", factory.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(employee,
                                LocalDateTime.of(2020, 1, 1, 10, 0), RoutePosition.OTHER,
                                false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOW"));
    }

    @Test
    void policyDeleteRemovesRule() throws Exception {
        String adminToken = factory.adminToken("admin-" + UUID.randomUUID().toString().substring(0, 8));
        Employee employee = dayShiftEmployee("FLOW-DEL", Gender.MAN);
        String token = factory.employeeToken(employee);

        String created = mockMvc.perform(post("/api/v1/policies")
                        .header("Authorization", factory.bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ruleType":"SHIFT_WINDOW","priority":5,"parameters":{}}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String ruleId = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(delete("/api/v1/policies/" + ruleId)
                        .header("Authorization", factory.bearer(token)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/policies/" + ruleId)
                        .header("Authorization", factory.bearer(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/policies/" + ruleId)
                        .header("Authorization", factory.bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void quotaFullDeniesWithQuotaExceeded() throws Exception {
        String fullCentre = "CC-FULL-" + UUID.randomUUID().toString().substring(0, 8);
        factory.quota(fullCentre, 0);
        Employee employee = factory.employee("FULL-" + UUID.randomUUID().toString().substring(0, 8), fullCentre,
                Gender.MAN, LocalTime.of(9, 0), LocalTime.of(18, 0));
        String token = factory.employeeToken(employee);

        BookingRequest request = new BookingRequest(employee.getId(),
                LocalDate.of(2026, 10, 15), BookingDirection.LOGIN, "A", "B",
                LocalDateTime.of(2026, 10, 15, 10, 0), RoutePosition.OTHER, false, false,
                false);
        mockMvc.perform(post("/api/v1/bookings/evaluate")
                        .header("Authorization", factory.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.decidingReason").value("QUOTA_EXCEEDED"));
    }

    @Test
    void approvalFlowConfirmsWithSeatReservation() throws Exception {
        Employee employee = dayShiftEmployee("FLOW-APPR", Gender.MAN);
        String token = factory.employeeToken(employee);
        String adminToken = factory.adminToken("admin-" + UUID.randomUUID().toString().substring(0, 8));
        String approverToken = factory.approverToken("approver-" + UUID.randomUUID().toString().substring(0, 8));

        String policies = mockMvc.perform(get("/api/v1/policies")
                        .header("Authorization", factory.bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String cutoffId = null;
        for (var node : objectMapper.readTree(policies)) {
            if ("BOOKING_CUTOFF".equals(node.get("ruleType").asText())) {
                cutoffId = node.get("id").asText();
            }
        }
        assertThat(cutoffId).isNotNull();
        mockMvc.perform(patch("/api/v1/policies/" + cutoffId + "/enabled")
                        .header("Authorization", factory.bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        String newRule = mockMvc.perform(post("/api/v1/policies")
                        .header("Authorization", factory.bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ruleType":"COST_CENTRE_QUOTA","priority":60,
                                 "hardDeny":false,"parameters":{}}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String softRuleId = objectMapper.readTree(newRule).get("id").asText();

        mockMvc.perform(post("/api/v1/policies")
                        .header("Authorization", factory.bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ruleType":"BOOKING_CUTOFF","priority":61,
                                 "hardDeny":false,"parameters":{"cutoffMinutes":999999}}"""))
                .andExpect(status().isCreated());

        String pending = mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", factory.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(employee,
                                LocalDateTime.of(2020, 1, 1, 10, 0), RoutePosition.OTHER,
                                false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.decision").value("NEEDS_APPROVAL"))
                .andReturn().getResponse().getContentAsString();
        UUID bookingId = UUID.fromString(objectMapper.readTree(pending).get("id").asText());

        mockMvc.perform(get("/api/v1/approvals/pending")
                        .header("Authorization", factory.bearer(approverToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(post("/api/v1/approvals/" + bookingId + "/approve")
                        .header("Authorization", factory.bearer(token)))
                .andExpect(status().isForbidden());

        var quotaBefore = quotaRepository.findByCostCentreCode(costCentre).orElseThrow();
        assertThat(quotaBefore.getReservedSeats()).isEqualTo(0);

        mockMvc.perform(post("/api/v1/approvals/" + bookingId + "/approve")
                        .header("Authorization", factory.bearer(approverToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(quotaRepository.findByCostCentreCode(costCentre).orElseThrow()
                .getReservedSeats()).isEqualTo(1);

        mockMvc.perform(post("/api/v1/approvals/" + bookingId + "/approve")
                        .header("Authorization", factory.bearer(approverToken)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/policies")
                        .header("Authorization", factory.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruleType\":\"SHIFT_WINDOW\",\"priority\":1}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/policies/" + softRuleId + "/enabled")
                        .header("Authorization", factory.bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    void approvalRejectMarksRejected() throws Exception {
        Employee employee = dayShiftEmployee("FLOW-REJ", Gender.MAN);
        String token = factory.employeeToken(employee);
        String adminToken = factory.adminToken("admin-" + UUID.randomUUID().toString().substring(0, 8));
        String approverToken = factory.approverToken("approver2-" + UUID.randomUUID().toString().substring(0, 8));

        String policies = mockMvc.perform(get("/api/v1/policies")
                        .header("Authorization", factory.bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (var node : objectMapper.readTree(policies)) {
            if ("BOOKING_CUTOFF".equals(node.get("ruleType").asText())) {
                mockMvc.perform(patch("/api/v1/policies/" + node.get("id").asText()
                                        + "/enabled")
                                .header("Authorization", factory.bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"enabled\":false}"))
                        .andExpect(status().isOk());
            }
        }
        mockMvc.perform(post("/api/v1/policies")
                        .header("Authorization", factory.bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ruleType":"BOOKING_CUTOFF","priority":70,
                                 "hardDeny":false,"parameters":{"cutoffMinutes":999999}}"""))
                .andExpect(status().isCreated());

        String pending = mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", factory.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(employee,
                                LocalDateTime.of(2020, 1, 1, 10, 0), RoutePosition.OTHER,
                                false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn().getResponse().getContentAsString();
        UUID bookingId = UUID.fromString(objectMapper.readTree(pending).get("id").asText());

        mockMvc.perform(post("/api/v1/approvals/" + bookingId + "/reject")
                        .header("Authorization", factory.bearer(approverToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(quotaRepository.findByCostCentreCode(costCentre).orElseThrow()
                .getReservedSeats()).isEqualTo(0);
    }

    @Test
    void storedExplanationSurvivesPolicyChange() throws Exception {
        Employee employee = dayShiftEmployee("FLOW-EXPL", Gender.MAN);
        String token = factory.employeeToken(employee);
        String adminToken = factory.adminToken("adminexpl-" + UUID.randomUUID().toString().substring(0, 8));

        String created = mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", factory.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(employee,
                                LocalDateTime.of(2026, 10, 15, 10, 0), RoutePosition.OTHER,
                                false)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID bookingId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        String policies = mockMvc.perform(get("/api/v1/policies")
                        .header("Authorization", factory.bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (var node : objectMapper.readTree(policies)) {
            mockMvc.perform(patch("/api/v1/policies/" + node.get("id").asText()
                                    + "/enabled")
                            .header("Authorization", factory.bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"enabled\":false}"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/explanation")
                        .header("Authorization", factory.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOW"))
                .andExpect(jsonPath("$.ruleResults").isArray())
                .andExpect(jsonPath("$.ruleResults").isNotEmpty());
    }

    private record BookingResponseCreated(UUID id) {
    }

    private BookingResponseCreated createBookingAs(Employee employee, String token)
            throws Exception {
        String created = mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", factory.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(employee,
                                LocalDateTime.of(2026, 10, 15, 10, 0), RoutePosition.OTHER,
                                false)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new BookingResponseCreated(
                UUID.fromString(objectMapper.readTree(created).get("id").asText()));
    }
}
