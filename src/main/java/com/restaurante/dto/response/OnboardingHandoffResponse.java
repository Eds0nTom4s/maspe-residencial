package com.restaurante.dto.response;

import com.restaurante.dto.business.BusinessProvisioningContracts.BusinessVertical;
import com.restaurante.dto.business.BusinessProvisioningContracts.PrincipalOwnerStrategy;
import com.restaurante.model.enums.OnboardingRequestStatus;
import com.restaurante.model.enums.TenantTipo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OnboardingHandoffResponse {
    public enum Availability { AVAILABLE, MISSING, REQUIRES_HUMAN_DECISION }

    private Long onboardingId;
    private Long onboardingVersion;
    private OnboardingRequestStatus status;
    private Long businessAccountId;
    private Long businessAccountVersion;
    private Long ownerResultUserId;
    private PrincipalOwnerStrategy ownerStrategy;
    private String confirmedPlanCode;
    private BusinessVertical vertical;
    private String normalizedNif;
    private String nomeNegocio;
    private TenantTipo tipoNegocio;
    private String telefone;
    private String email;
    private String provisioningOperationId;
    private Long tenantId;
    private Map<String, Availability> fieldMatrix;
}
