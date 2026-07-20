package com.restaurante.dto.response;

import com.restaurante.model.enums.OnboardingPaymentStatus;
import com.restaurante.model.enums.OnboardingRequestStatus;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.OnboardingAccountChoice;
import com.restaurante.model.enums.OnboardingNifResolution;
import com.restaurante.dto.business.BusinessProvisioningContracts.BusinessVertical;
import com.restaurante.dto.business.BusinessProvisioningContracts.PrincipalOwnerStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnboardingRequestResponse {

    private Long id;
    private Long version;
    private String contractVersion;
    private Boolean legacyStatusIndicator;
    private String channel;
    private String nomeSolicitante;
    private String telefone;
    private String email;
    private String nomeNegocio;
    private String nif;
    private String normalizedNif;
    private OnboardingNifResolution nifResolution;
    private BusinessAccountCandidate businessAccountCandidate;
    private TenantTipo tipoNegocio;
    private String planoCodigo;
    private String planoNome;
    private String confirmedPlanCode;
    private String confirmedPlanName;
    private BusinessVertical vertical;
    private OnboardingAccountChoice accountChoice;
    private PrincipalOwnerStrategy ownerStrategy;
    private Long ownerResultUserId;
    private Long businessAccountId;
    private String businessAccountNome;
    private String businessAccountEstado;
    private Long businessAccountVersion;
    private Long tenantId;
    private String tenantNome;
    private String tenantEstado;
    private Long tenantVersion;
    private String provisioningOperationId;
    private String provisioningOperationStatus;
    private Boolean provisioningEffectsCommitted;
    private OnboardingRequestStatus status;
    private OnboardingPaymentStatus statusPagamento;
    private BigDecimal valor;
    private String moeda;
    private String observacao;
    private String motivoRejeicao;
    private String approvalReason;
    private String cancellationReason;
    private String notificationStatus;
    private String notificationMessage;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime completedAt;
    private LocalDateTime activatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class BusinessAccountCandidate {
        private Long id;
        private Long version;
        private String nome;
        private String nif;
        private String estado;
    }
}
