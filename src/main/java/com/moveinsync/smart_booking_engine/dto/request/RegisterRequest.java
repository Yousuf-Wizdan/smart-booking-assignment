package com.moveinsync.smart_booking_engine.dto.request;

import com.moveinsync.smart_booking_engine.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

/**
 * Public self-registration always creates an {@code EMPLOYEE} account plus its
 * employee profile. ADMIN and APPROVER accounts are never self-registered.
 */
public record RegisterRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 32) String employeeCode,
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Size(max = 64) String businessUnit,
        @NotBlank @Size(max = 32) String costCentreCode,
        @NotNull Gender gender,
        @NotNull LocalTime shiftStart,
        @NotNull LocalTime shiftEnd
) {
}
