package com.moveinsync.smart_booking_engine.dto.response;

import com.moveinsync.smart_booking_engine.enums.Role;
import java.util.UUID;

/** The caller's own identity: no token reissue, no secrets. */
public record MeResponse(
        String username,
        Role role,
        UUID employeeId
) {
}
