package com.moveinsync.smart_booking_engine.integration;

import com.moveinsync.smart_booking_engine.entity.Employee;
import com.moveinsync.smart_booking_engine.enums.Gender;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Test-only helpers: employees, logins, auth headers. Not part of the app. */
@Component
public class TestDataFactory {

    @Autowired
    com.moveinsync.smart_booking_engine.repository.EmployeeRepository employees;

    @Autowired
    com.moveinsync.smart_booking_engine.repository.AppUserRepository users;

    @Autowired
    com.moveinsync.smart_booking_engine.repository.CostCentreQuotaRepository quotas;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    com.moveinsync.smart_booking_engine.security.JwtService jwtService;

    public Employee employee(String code, String costCentre, Gender gender,
                             LocalTime shiftStart, LocalTime shiftEnd) {
        return employees.save(new Employee(code, "Test " + code, "BU-TEST",
                costCentre, gender, shiftStart, shiftEnd));
    }

    public com.moveinsync.smart_booking_engine.entity.CostCentreQuota quota(String costCentre,
                                                                            int capacity) {
        return quotas.save(new com.moveinsync.smart_booking_engine.entity.CostCentreQuota(
                costCentre, capacity));
    }

    public String token(String username, com.moveinsync.smart_booking_engine.enums.Role role,
                        UUID employeeId) {
        var details = new com.moveinsync.smart_booking_engine.security.CustomUserDetails(
                UUID.randomUUID(), username, "hash", role.name(), employeeId, true);
        return jwtService.generateToken(details);
    }

    public String employeeToken(Employee employee) {
        String username = ("user-" + employee.getEmployeeCode()
                .toLowerCase().replaceAll("[^a-z0-9-]", "-")).substring(0,
                Math.min(60, ("user-" + employee.getEmployeeCode()).length()));
        users.save(new com.moveinsync.smart_booking_engine.entity.AppUser(
                username,
                passwordEncoder.encode("password123"),
                com.moveinsync.smart_booking_engine.enums.Role.EMPLOYEE, employee, true));
        return token(username,
                com.moveinsync.smart_booking_engine.enums.Role.EMPLOYEE, employee.getId());
    }

    /** Persists a real ADMIN account (the JWT filter reloads users from the DB). */
    public String adminToken(String username) {
        users.save(new com.moveinsync.smart_booking_engine.entity.AppUser(
                username, passwordEncoder.encode("password123"),
                com.moveinsync.smart_booking_engine.enums.Role.ADMIN, null, true));
        return token(username, com.moveinsync.smart_booking_engine.enums.Role.ADMIN, null);
    }

    /** Persists a real APPROVER account (the JWT filter reloads users from the DB). */
    public String approverToken(String username) {
        users.save(new com.moveinsync.smart_booking_engine.entity.AppUser(
                username, passwordEncoder.encode("password123"),
                com.moveinsync.smart_booking_engine.enums.Role.APPROVER, null, true));
        return token(username, com.moveinsync.smart_booking_engine.enums.Role.APPROVER, null);
    }

    public String bearer(String token) {
        return "Bearer " + token;
    }
}
