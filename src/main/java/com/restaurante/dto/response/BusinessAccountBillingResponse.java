package com.restaurante.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessAccountBillingResponse {

    private Long businessAccountId;
    private String businessAccountNome;
    private String moeda;
    private Long linkedTenantCount;
    private Long activeSubscriptionCount;
    private BigDecimal totalMensalEstimado;
    private List<String> legacyPlanCodes;
    private List<String> billingPlanCodes;
    private List<TenantBillingItem> tenants;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TenantBillingItem {
        private Long tenantId;
        private String tenantNome;
        private String tenantCode;
        private String legacyPlanCode;
        private String legacyPlanName;
        private String legacySubscriptionStatus;
        private String billingPlanCode;
        private String billingPlanName;
        private String billingSubscriptionStatus;
        private String currency;
        private BigDecimal monthlyAmount;
        private LocalDate legacySubscriptionEndDate;
        private LocalDateTime billingCurrentPeriodEnd;
        private Boolean autoRenew;
    }
}
