package com.restaurante.delivery.service;

import com.restaurante.delivery.repository.CourierEarningRepository;
import com.restaurante.model.entity.CourierEarning;
import com.restaurante.model.entity.DeliveryJob;
import com.restaurante.model.enums.CourierEarningStatus;
import com.restaurante.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CourierEarningService {

    private final CourierEarningRepository earningRepository;

    public CourierEarningService(CourierEarningRepository earningRepository) {
        this.earningRepository = earningRepository;
    }

    public CourierEarning createEarning(DeliveryJob job) {
        if (job.getCourier() == null) {
            throw new BusinessException("COURIER_RELIABILITY_PROFILE_NOT_FOUND");
        }

        Optional<CourierEarning> existing = earningRepository.findByDeliveryJobId(job.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        CourierEarning earning = new CourierEarning();
        earning.setCourier(job.getCourier());
        earning.setDeliveryJob(job);
        earning.setTenant(job.getTenant());
        earning.setPedido(job.getPedido());
        earning.setStatus(CourierEarningStatus.PENDING_DELIVERY);
        earning.setCurrency(job.getDeliveryFeeCurrency() != null ? job.getDeliveryFeeCurrency() : "AOA");
        earning.setDeliveryFeeAmount(job.getFinalDeliveryFee());
        earning.setCourierEarningAmount(job.getCourierEarningAmount());
        earning.setConsumaCommissionAmount(job.getConsumaCommissionAmount());
        earning.setTenantSubsidyAmount(job.getTenantSubsidyAmount() != null ? job.getTenantSubsidyAmount() : java.math.BigDecimal.ZERO);

        return earningRepository.save(earning);
    }

    public CourierEarning earn(Long deliveryJobId) {
        CourierEarning earning = earningRepository.findByDeliveryJobId(deliveryJobId)
                .orElseThrow(() -> new BusinessException("DELIVERY_EARNING_NOT_FOUND"));

        if (earning.getStatus() == CourierEarningStatus.EARNED || earning.getStatus() == CourierEarningStatus.PAYABLE) {
            return earning;
        }

        earning.setStatus(CourierEarningStatus.PAYABLE);
        earning.setEarnedAt(LocalDateTime.now());
        earning.setPayableAt(LocalDateTime.now());
        return earningRepository.save(earning);
    }

    public CourierEarning cancel(Long deliveryJobId) {
        CourierEarning earning = earningRepository.findByDeliveryJobId(deliveryJobId)
                .orElseThrow(() -> new BusinessException("DELIVERY_EARNING_NOT_FOUND"));

        earning.setStatus(CourierEarningStatus.CANCELLED);
        earning.setCancelledAt(LocalDateTime.now());
        return earningRepository.save(earning);
    }

    public CourierEarning hold(Long deliveryJobId, String reason) {
        CourierEarning earning = earningRepository.findByDeliveryJobId(deliveryJobId)
                .orElseThrow(() -> new BusinessException("DELIVERY_EARNING_NOT_FOUND"));

        earning.setStatus(CourierEarningStatus.HELD);
        earning.setHeldAt(LocalDateTime.now());
        earning.setDisputeReason(reason);
        return earningRepository.save(earning);
    }

    public List<CourierEarning> getCourierEarnings(Long courierId) {
        return earningRepository.findByCourierId(courierId);
    }

    public List<CourierEarning> getCourierEarningsByStatus(Long courierId, CourierEarningStatus status) {
        return earningRepository.findByCourierIdAndStatus(courierId, status);
    }
}
