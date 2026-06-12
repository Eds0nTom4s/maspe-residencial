package com.restaurante.dto.response;

import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantProvisioningAccessResponse {
    private Long businessAccountId;
    private String businessAccountNome;
    private Long tenantId;
    private String tenantNome;
    private String tenantSlug;
    private TenantTipo tenantTipo;
    private TenantEstado tenantEstado;
    private Long ownerUserId;
    private String ownerUsername;
    private String ownerNome;
    private String ownerEmail;
    private String ownerTelefone;
    private Long tenantUserId;
    private Long businessAccountMemberId;
    private String temporaryPassword;
    private Boolean mustChangePassword;
    private LocalDateTime temporaryPasswordExpiresAt;
    private String loginUrl;
    private Boolean tenantSelectRequired;
    private String qrToken;
    private String qrUrlPublica;
    private List<String> warnings;
    private LocalDateTime createdAt;
}
