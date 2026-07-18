package com.restaurante.dto.business;

import com.restaurante.businesstemplate.dto.BusinessTemplatePreviewResponse;
import com.restaurante.businesstemplate.dto.BusinessTemplateProvisionRequest;
import com.restaurante.businesstemplate.dto.BusinessTemplateProvisionResponse;
import com.restaurante.model.enums.BusinessAccountMemberEstado;
import com.restaurante.model.enums.BusinessAccountRole;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class BusinessProvisioningContracts {
    public static final String CONTRACT_VERSION = "BUSINESS_PROVISIONING_V1";
    private BusinessProvisioningContracts() {}

    public enum PrincipalOwnerStrategy { ASSOCIATE_EXISTING, CREATE_NEW }
    public enum BusinessVertical { CONSUMA_PONTO, CONSUMA_REST }
    public enum OperationalAccessStrategy { ACCOUNT_OWNER_AS_TENANT_OWNER }

    public record PrincipalOwner(
            @NotNull PrincipalOwnerStrategy strategy,
            Long userId,
            Boolean confirmExistingUser,
            String username,
            @Size(min = 12, max = 200) String temporaryPassword,
            String nome,
            @Email String email,
            String telefone
    ) {}

    public record CanonicalAccountCreateRequest(
            @NotBlank String nome,
            @NotBlank String slug,
            String nif,
            @Email String email,
            String telefone,
            @Positive Integer maxTenants,
            @Valid @NotNull PrincipalOwner responsavelPrincipal
    ) {}

    public record ReplaceOwnerRequest(
            @NotNull Long accountVersion,
            @Valid @NotNull PrincipalOwner novoResponsavel,
            @NotBlank @Size(max = 500) String reason
    ) {}

    public record ManagerCommandRequest(
            @NotNull Long accountVersion,
            @NotNull Long userId,
            @NotNull BusinessAccountRole role,
            BusinessAccountMemberEstado estado,
            @NotBlank @Size(max = 500) String reason
    ) {}

    public record AccountActivationRequest(
            @NotNull Long accountVersion,
            @NotBlank @Size(max = 500) String reason
    ) {}

    public record BusinessData(
            @NotBlank String nomeNegocio,
            @NotBlank String slug,
            String tenantCode,
            @NotNull TenantTipo tipo,
            String nif,
            String telefone,
            @Email String email,
            String endereco,
            String provincia,
            String municipio
    ) {}

    public record AdditionalOperationalAccess(
            @NotNull Long userId,
            @NotNull TenantUserRole tenantRole
    ) {}

    public record AccessConfiguration(
            @NotNull OperationalAccessStrategy strategy,
            @Valid List<AdditionalOperationalAccess> additionalAccesses
    ) {}

    public record BusinessPreviewRequest(
            @NotNull Long accountVersion,
            @NotBlank String planoCodigo,
            @NotNull BusinessVertical vertical,
            @Valid @NotNull BusinessData negocio,
            @Valid BusinessTemplateProvisionRequest.PontoOptions ponto,
            @Valid BusinessTemplateProvisionRequest.RestOptions rest,
            @Valid @NotNull AccessConfiguration acessos
    ) {}

    public record BusinessPreviewResponse(
            String previewId,
            String requestFingerprint,
            LocalDateTime expiresAt,
            Long accountId,
            List<String> entidadesPlaneadas,
            String planoCodigo,
            BusinessTemplatePreviewResponse.PlanLimitsPreview limites,
            BusinessVertical vertical,
            String templateCode,
            Integer templateVersion,
            BusinessTemplatePreviewResponse.PlanResources recursos,
            BusinessTemplatePreviewResponse.TemplatePoliciesPreview politicas,
            List<BusinessTemplatePreviewResponse.ValidationMessage> warnings,
            List<BusinessTemplatePreviewResponse.ValidationMessage> blockers,
            Boolean allowedToProvision,
            Boolean replay
    ) {}

    public record BusinessProvisionRequest(
            @NotBlank String previewId,
            @NotBlank String requestFingerprint,
            @NotNull Long accountVersion,
            @NotNull Boolean confirmed
    ) {}

    public record ProvisioningOperationResponse(
            String operationId,
            Long businessAccountId,
            String idempotencyKey,
            String requestFingerprint,
            String previewId,
            String status,
            Long tenantId,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            String errorCode,
            String errorMessage,
            String correlationId,
            Boolean retryAllowed,
            LocalDateTime nextRetryAt,
            Integer attemptCount,
            LocalDateTime leaseUntil,
            Boolean effectsCommitted,
            Boolean terminal,
            Boolean replay,
            BusinessTemplateProvisionResponse result
    ) {}

    public record ReadinessCheck(String code, boolean ready, String detail) {}

    public record BusinessReadinessResponse(
            Long businessAccountId,
            Long tenantId,
            String estado,
            Long accountVersion,
            Long tenantVersion,
            boolean ready,
            List<ReadinessCheck> checks,
            List<String> blockers
    ) {}

    public record BusinessActivationRequest(
            @NotNull Long accountVersion,
            @NotNull Long tenantVersion,
            @NotBlank @Size(max = 500) String reason
    ) {}
}
