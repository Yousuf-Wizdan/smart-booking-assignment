package com.moveinsync.smart_booking_engine.dto.response;

import com.moveinsync.smart_booking_engine.enums.Role;
import java.util.UUID;

public record TokenResponse(
        String token,
        String tokenType,
        String username,
        Role role,
        UUID employeeId
) {
}
