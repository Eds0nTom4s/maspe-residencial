package com.restaurante.delivery.service;

import com.restaurante.delivery.repository.CourierEarningRepository;
import com.restaurante.delivery.repository.CourierSettlementBatchRepository;
import com.restaurante.delivery.repository.CourierSettlementLineRepository;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.*;
import com.restaurante.service.operacional.OperationalEventLogService;
import com.restaurante.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class CourierSettlementService {

    private final CourierEarningRepository earningRepository;
    private final CourierSettlementBatchRepository batchRepository;
    private final CourierSettlementLineRepository lineRepository;
    private final OperationalEventLogService operationalEventLogService;

    public CourierSettlementService(CourierEarningRepository earningRepository,
                                    CourierSettlementBatchRepository batchRepository,
                                    CourierSettlementLineRepository lineRepository,
                                    OperationalEventLogService operationalEventLogService) {
        this.earningRepository = earningRepository;
        this.batchRepository = batchRepository;
        this.lineRepository = lineRepository;
        this.operationalEventLogService = operationalEventLogService;
    }

    public CourierSettlementBatch calculateBatch(LocalDateTime start, LocalDateTime end) {
        // In a real database, we would query by earnedAt/payableAt date range and status = PAYABLE.
        // For the MVP logic, we will fetch all earnings and filter.
        List<CourierEarning> allEarnings = earningRepository.findAll();
        List<CourierEarning> payableEarnings = new ArrayList<>();

        for (CourierEarning earning : allEarnings) {
            if (earning.getStatus() == CourierEarningStatus.PAYABLE) {
                LocalDateTime dateToCheck = earning.getPayableAt() != null ? earning.getPayableAt() : earning.getCreatedAt();
                if (dateToCheck != null && !dateToCheck.isBefore(start) && !dateToCheck.isAfter(end)) {
                    payableEarnings.add(earning);
                }
            }
        }

        if (payableEarnings.isEmpty()) {
            throw new BusinessException("NO_PAYABLE_EARNINGS_FOUND");
        }

        String batchNumber = "SETTLE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        
        CourierSettlementBatch batch = new CourierSettlementBatch();
        batch.setBatchNumber(batchNumber);
        batch.setStatus(CourierSettlementBatchStatus.CALCULATED);
        batch.setPeriodStart(start);
        batch.setPeriodEnd(end);
        batch.setCurrency("AOA");

        BigDecimal totalEarnings = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;

        batch = batchRepository.save(batch);

        for (CourierEarning earning : payableEarnings) {
            totalEarnings = totalEarnings.add(earning.getCourierEarningAmount());
            totalCommission = totalCommission.add(earning.getConsumaCommissionAmount());

            CourierSettlementLine line = new CourierSettlementLine();
            line.setBatch(batch);
            line.setCourier(earning.getCourier());
            line.setCourierEarning(earning);
            line.setDeliveryJob(earning.getDeliveryJob());
            line.setAmount(earning.getCourierEarningAmount());
            line.setStatus(CourierSettlementLineStatus.PAYABLE);
            lineRepository.save(line);

            // Transition earning status to PAID
            earning.setStatus(CourierEarningStatus.PAID);
            earning.setPaidAt(LocalDateTime.now());
            earningRepository.save(earning);

            // Also update DeliveryJob settlementStatus
            DeliveryJob job = earning.getDeliveryJob();
            job.setSettlementStatus(CourierSettlementStatus.PAID);
        }

        batch.setTotalEarningsAmount(totalEarnings);
        batch.setTotalCommissionAmount(totalCommission);
        batch.setTotalJobs(payableEarnings.size());
        batchRepository.save(batch);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("batchNumber", batchNumber);
        metadata.put("totalEarnings", totalEarnings);
        metadata.put("totalCommission", totalCommission);
        metadata.put("totalJobs", payableEarnings.size());

        operationalEventLogService.logGenericForTenant(
                1L, // global / system scope tenant
                OperationalEventType.COURIER_SETTLEMENT_BATCH_CALCULATED,
                OperationalEntityType.COURIER_SETTLEMENT_BATCH,
                batch.getId(),
                OperationalOrigem.SYSTEM,
                "Settlement batch calculated and approved",
                metadata,
                "127.0.0.1",
                "Consuma Ledger Worker"
        );

        return batch;
    }
}
