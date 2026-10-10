package com.moveinsync.smart_booking_engine.service;

import com.moveinsync.smart_booking_engine.dto.request.EmployeeCreateRequest;
import com.moveinsync.smart_booking_engine.dto.request.EmployeeUpdateRequest;
import com.moveinsync.smart_booking_engine.dto.response.EmployeeResponse;
import com.moveinsync.smart_booking_engine.entity.Employee;
import com.moveinsync.smart_booking_engine.exception.ConflictException;
import com.moveinsync.smart_booking_engine.exception.ForbiddenException;
import com.moveinsync.smart_booking_engine.exception.ResourceNotFoundException;
import com.moveinsync.smart_booking_engine.repository.EmployeeRepository;
import com.moveinsync.smart_booking_engine.security.AuthenticatedUser;
import com.moveinsync.smart_booking_engine.security.CustomUserDetails;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Employee management. Writes are admin-only (enforced by the URL rules);
 * reads allow an employee to see only their own profile.
 */
@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public EmployeeResponse create(EmployeeCreateRequest request) {
        if (employeeRepository.existsByEmployeeCode(request.employeeCode())) {
            throw new ConflictException("Employee code already exists: " + request.employeeCode());
        }
        Employee employee = new Employee(request.employeeCode(), request.fullName(),
                request.businessUnit(), request.costCentreCode(), request.gender(),
                request.shiftStart(), request.shiftEnd());
        if (request.active() != null) {
            employee.setActive(request.active());
        }
        return EmployeeResponse.from(employeeRepository.save(employee));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(UUID id) {
        CustomUserDetails user = AuthenticatedUser.require();
        if (!user.isAdmin() && !id.equals(user.getEmployeeId())) {
            throw new ForbiddenException("Employees may only read their own profile");
        }
        return EmployeeResponse.from(findById(id));
    }

    @Transactional(readOnly = true)
    public Page<EmployeeResponse> list(Pageable pageable) {
        CustomUserDetails user = AuthenticatedUser.require();
        if (!user.isAdmin()) {
            Employee own = findById(user.getEmployeeId());
            return new PageImpl<>(java.util.List.of(EmployeeResponse.from(own)));
        }
        return employeeRepository.findAll(pageable).map(EmployeeResponse::from);
    }

    @Transactional
    public EmployeeResponse update(UUID id, EmployeeUpdateRequest request) {
        Employee employee = findById(id);
        employee.setFullName(request.fullName());
        employee.setBusinessUnit(request.businessUnit());
        employee.setCostCentreCode(request.costCentreCode());
        employee.setGender(request.gender());
        employee.setShiftStart(request.shiftStart());
        employee.setShiftEnd(request.shiftEnd());
        employee.setActive(request.active());
        return EmployeeResponse.from(employeeRepository.save(employee));
    }

    private Employee findById(UUID id) {
        if (id == null) {
            throw new ResourceNotFoundException("Employee not found");
        }
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));
    }
}
