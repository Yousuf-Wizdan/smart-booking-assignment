package com.moveinsync.smart_booking_engine.service;

import com.moveinsync.smart_booking_engine.dto.request.QuotaCreateRequest;
import com.moveinsync.smart_booking_engine.dto.request.QuotaUpdateRequest;
import com.moveinsync.smart_booking_engine.dto.response.QuotaResponse;
import com.moveinsync.smart_booking_engine.entity.CostCentreQuota;
import com.moveinsync.smart_booking_engine.exception.BadRequestException;
import com.moveinsync.smart_booking_engine.exception.ConflictException;
import com.moveinsync.smart_booking_engine.exception.ResourceNotFoundException;
import com.moveinsync.smart_booking_engine.repository.CostCentreQuotaRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin management of seat quotas. Booking flows never use these methods. */
@Service
public class QuotaService {

    private final CostCentreQuotaRepository quotaRepository;

    public QuotaService(CostCentreQuotaRepository quotaRepository) {
        this.quotaRepository = quotaRepository;
    }

    @Transactional(readOnly = true)
    public List<QuotaResponse> list() {
        return quotaRepository.findAll().stream().map(QuotaResponse::from).toList();
    }

    @Transactional
    public QuotaResponse create(QuotaCreateRequest request) {
        if (quotaRepository.findByCostCentreCode(request.costCentreCode()).isPresent()) {
            throw new ConflictException(
                    "Quota already exists for cost centre: " + request.costCentreCode());
        }
        return QuotaResponse.from(quotaRepository.save(
                new CostCentreQuota(request.costCentreCode(), request.capacity())));
    }

    @Transactional
    public QuotaResponse update(UUID id, QuotaUpdateRequest request) {
        CostCentreQuota quota = quotaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quota not found: " + id));
        int reserved = request.reservedSeats() == null ? quota.getReservedSeats()
                : request.reservedSeats();
        if (request.capacity() < reserved) {
            throw new BadRequestException("Capacity (" + request.capacity()
                    + ") cannot be below reserved seats (" + reserved + ")");
        }
        quota.setCapacity(request.capacity());
        if (request.reservedSeats() != null) {
            quota.setReservedSeats(request.reservedSeats());
        }
        return QuotaResponse.from(quotaRepository.save(quota));
    }
}
