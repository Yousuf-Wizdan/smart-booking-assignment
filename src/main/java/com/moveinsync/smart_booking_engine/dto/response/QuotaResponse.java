package com.moveinsync.smart_booking_engine.dto.response;

import com.moveinsync.smart_booking_engine.entity.CostCentreQuota;
import java.util.UUID;

public record QuotaResponse(
        UUID id,
        String costCentreCode,
        int capacity,
        int reservedSeats,
        int freeSeats
) {

    public static QuotaResponse from(CostCentreQuota quota) {
        return new QuotaResponse(
                quota.getId(),
                quota.getCostCentreCode(),
                quota.getCapacity(),
                quota.getReservedSeats(),
                quota.getCapacity() - quota.getReservedSeats());
    }
}
