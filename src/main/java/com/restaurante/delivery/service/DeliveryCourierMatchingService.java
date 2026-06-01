package com.restaurante.delivery.service;

import com.restaurante.delivery.repository.CourierProfileRepository;
import com.restaurante.delivery.repository.CourierReliabilityProfileRepository;
import com.restaurante.model.entity.CourierReliabilityProfile;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.CourierProfile;
import com.restaurante.model.entity.ItemPedido;
import com.restaurante.model.entity.ProductDeliveryPolicy;
import com.restaurante.model.enums.CourierAvailability;
import com.restaurante.model.enums.CourierStatus;
import com.restaurante.model.enums.CourierVehicleType;
import com.restaurante.model.enums.CourierVerificationStatus;
import com.restaurante.repository.ItemPedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeliveryCourierMatchingService {

    private final CourierProfileRepository courierProfileRepository;
    private final CourierReliabilityProfileRepository reliabilityProfileRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final ProductDeliveryPolicyService productDeliveryPolicyService;

    @Transactional(readOnly = true)
    public List<CourierProfile> findMatchingCouriers(Long tenantId, Long pedidoId, BigDecimal pickupLat, BigDecimal pickupLng, BigDecimal maxDistanceKm) {
        if (pickupLat == null || pickupLng == null) {
            return List.of();
        }

        // 1. Fetch available nearby candidates (updated location in the last 12 hours)
        LocalDateTime minLocationTime = LocalDateTime.now().minusHours(12);
        List<CourierProfile> candidates = courierProfileRepository.findAvailableNearbyCandidates(
                CourierStatus.ACTIVE,
                CourierVerificationStatus.VERIFIED,
                CourierAvailability.ONLINE_AVAILABLE,
                minLocationTime
        );

        // 2. Fetch order items to assess product policy constraints (vehicle compatibility)
        List<ItemPedido> items = itemPedidoRepository.findByPedidoId(pedidoId);
        boolean allowMotorbike = true;
        boolean allowCar = true;
        BigDecimal restrictedMaxDistance = maxDistanceKm;

        for (ItemPedido item : items) {
            if (item == null || item.getProduto() == null) continue;
            ProductDeliveryPolicy policy = productDeliveryPolicyService.getOrNull(tenantId, item.getProduto().getId());
            if (policy != null) {
                if (!policy.isAllowMotorbikeDelivery()) {
                    allowMotorbike = false;
                }
                if (!policy.isAllowCarDelivery()) {
                    allowCar = false;
                }
                if (policy.getMaxDeliveryDistanceKm() != null) {
                    if (restrictedMaxDistance == null || policy.getMaxDeliveryDistanceKm().compareTo(restrictedMaxDistance) < 0) {
                        restrictedMaxDistance = policy.getMaxDeliveryDistanceKm();
                    }
                }
            }
        }

        List<MatchedCourier> matched = new ArrayList<>();
        double pLat = pickupLat.doubleValue();
        double pLng = pickupLng.doubleValue();

        for (CourierProfile c : candidates) {
            if (c.getCurrentLatitude() == null || c.getCurrentLongitude() == null) continue;

            // Vehicle compatibility filter
            if (c.getVehicleType() == CourierVehicleType.MOTORBIKE && !allowMotorbike) continue;
            if (c.getVehicleType() == CourierVehicleType.CAR && !allowCar) continue;

            double cLat = c.getCurrentLatitude().doubleValue();
            double cLng = c.getCurrentLongitude().doubleValue();
            double dist = calculateDistanceKm(pLat, pLng, cLat, cLng);

            // Distance threshold check
            if (restrictedMaxDistance != null && dist > restrictedMaxDistance.doubleValue()) {
                continue;
            }

            matched.add(new MatchedCourier(c, dist));
        }

        // Fetch reliability scores to avoid N+1 queries
        Map<Long, Integer> scoreMap = new HashMap<>();
        for (MatchedCourier mc : matched) {
            int score = reliabilityProfileRepository.findByCourierId(mc.courier().getId())
                    .map(CourierReliabilityProfile::getScore)
                    .orElse(100);
            scoreMap.put(mc.courier().getId(), score);
        }

        // Sort by reliability score (descending), then by distance (closest first)
        matched.sort((m1, m2) -> {
            int score1 = scoreMap.getOrDefault(m1.courier().getId(), 100);
            int score2 = scoreMap.getOrDefault(m2.courier().getId(), 100);
            if (score1 != score2) {
                return Integer.compare(score2, score1);
            }
            return Double.compare(m1.distance(), m2.distance());
        });

        return matched.stream().map(MatchedCourier::courier).toList();
    }

    private double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371.0; // kilometers
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private record MatchedCourier(CourierProfile courier, double distance) {}
}
