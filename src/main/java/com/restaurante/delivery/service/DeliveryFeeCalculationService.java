package com.restaurante.delivery.service;

import com.restaurante.delivery.dto.request.DeliveryFeeCalculationRequest;
import com.restaurante.delivery.repository.DeliveryPricingPolicyRepository;
import com.restaurante.model.entity.DeliveryPricingPolicy;
import com.restaurante.model.enums.PackageSize;
import com.restaurante.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DeliveryFeeCalculationService {

    private final DeliveryPricingPolicyRepository pricingPolicyRepository;

    public DeliveryFeeCalculationService(DeliveryPricingPolicyRepository pricingPolicyRepository) {
        this.pricingPolicyRepository = pricingPolicyRepository;
    }

    public DeliveryPricingPolicy findActivePolicy(Long tenantId) {
        LocalDateTime now = LocalDateTime.now();
        List<DeliveryPricingPolicy> policies = pricingPolicyRepository.findActivePolicies(tenantId, now);
        if (policies.isEmpty()) {
            throw new BusinessException("DELIVERY_PRICING_POLICY_NOT_FOUND");
        }
        return policies.get(0);
    }

    public DeliveryFeeQuoteCalculationResult calculate(DeliveryFeeCalculationRequest request) {
        if (request.getDistanceKm() == null || request.getDistanceKm().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("DELIVERY_FEE_INVALID_DISTANCE");
        }

        DeliveryPricingPolicy policy = findActivePolicy(request.getTenantId());
        
        // rawFee = baseFee + (distanceKm * perKmFee)
        BigDecimal baseFee = policy.getBaseFeeAmount();
        BigDecimal perKmFee = policy.getPerKmFeeAmount();
        BigDecimal rawFee = baseFee.add(request.getDistanceKm().multiply(perKmFee));

        // package size surcharge
        if (request.getPackageSize() == PackageSize.LARGE || request.getPackageSize() == PackageSize.EXTRA_LARGE) {
            rawFee = rawFee.add(policy.getLargePackageSurcharge());
        }

        // fragile surcharge
        if (Boolean.TRUE.equals(request.getFragile())) {
            rawFee = rawFee.add(policy.getFragilePackageSurcharge());
        }

        // peak multiplier
        if (policy.getPeakMultiplier() != null && policy.getPeakMultiplier().compareTo(BigDecimal.ONE) > 0) {
            rawFee = rawFee.multiply(policy.getPeakMultiplier());
        }

        // finalFee = max(rawFee, minimumFee)
        BigDecimal finalFee = rawFee.max(policy.getMinimumFeeAmount());

        // maximum fee limit
        if (policy.getMaximumFeeAmount() != null) {
            finalFee = finalFee.min(policy.getMaximumFeeAmount());
        }

        // Rounding
        finalFee = finalFee.setScale(2, RoundingMode.HALF_UP);

        // Tenant subsidy
        BigDecimal subsidy = request.getTenantSubsidyAmount() != null ? request.getTenantSubsidyAmount() : BigDecimal.ZERO;
        BigDecimal customerPays = finalFee.subtract(subsidy).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        // Earnings and Commission
        BigDecimal courierShare = policy.getCourierSharePercentage().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal consumaCommission = policy.getConsumaCommissionPercentage().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

        BigDecimal courierEarning = finalFee.multiply(courierShare).setScale(2, RoundingMode.HALF_UP);
        BigDecimal consumaCommissionAmount = finalFee.multiply(consumaCommission).setScale(2, RoundingMode.HALF_UP);

        return DeliveryFeeQuoteCalculationResult.builder()
                .policy(policy)
                .baseFeeAmount(baseFee)
                .distanceFeeAmount(request.getDistanceKm().multiply(perKmFee).setScale(2, RoundingMode.HALF_UP))
                .surchargeAmount(finalFee.subtract(baseFee).subtract(request.getDistanceKm().multiply(perKmFee)).setScale(2, RoundingMode.HALF_UP))
                .finalFeeAmount(finalFee)
                .tenantSubsidyAmount(subsidy)
                .customerPaysAmount(customerPays)
                .courierEarningAmount(courierEarning)
                .consumaCommissionAmount(consumaCommissionAmount)
                .build();
    }

    @lombok.Value
    @lombok.Builder
    public static class DeliveryFeeQuoteCalculationResult {
        DeliveryPricingPolicy policy;
        BigDecimal baseFeeAmount;
        BigDecimal distanceFeeAmount;
        BigDecimal surchargeAmount;
        BigDecimal finalFeeAmount;
        BigDecimal tenantSubsidyAmount;
        BigDecimal customerPaysAmount;
        BigDecimal courierEarningAmount;
        BigDecimal consumaCommissionAmount;
    }
}
