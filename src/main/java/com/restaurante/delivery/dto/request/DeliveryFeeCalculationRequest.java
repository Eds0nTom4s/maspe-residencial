package com.restaurante.delivery.dto.request;

import com.restaurante.model.enums.PackageSize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryFeeCalculationRequest {
    private Long tenantId;
    private Long pedidoId;
    private Long deliveryJobId;
    private BigDecimal distanceKm;
    private PackageSize packageSize;
    private Boolean fragile;
    private LocalDateTime requestedAt;
    private BigDecimal tenantSubsidyAmount;
    private BigDecimal orderAmount;
}
