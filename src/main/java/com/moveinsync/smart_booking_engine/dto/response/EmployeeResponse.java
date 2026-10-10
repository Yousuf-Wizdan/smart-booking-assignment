package com.moveinsync.smart_booking_engine.dto.response;

import com.moveinsync.smart_booking_engine.entity.Employee;
import com.moveinsync.smart_booking_engine.enums.Gender;
import java.time.LocalTime;
import java.util.UUID;

public record EmployeeResponse(
        UUID id,
        String employeeCode,
        String fullName,
        String businessUnit,
        String costCentreCode,
        Gender gender,
        LocalTime shiftStart,
        LocalTime shiftEnd,
        boolean active
) {

    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFullName(),
                employee.getBusinessUnit(),
                employee.getCostCentreCode(),
                employee.getGender(),
                employee.getShiftStart(),
                employee.getShiftEnd(),
                employee.isActive());
    }
}
