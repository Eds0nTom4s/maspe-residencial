package com.restaurante.dto.response;

import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.model.enums.BusinessAccountMemberEstado;
import com.restaurante.model.enums.BusinessAccountRole;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessAccountGovernanceDiagnosticResponse {

    private Long businessAccountId;
    private String businessAccountNome;
    private String businessAccountSlug;
    private BusinessAccountEstado estado;
    private Long responsavelUserId;
    private String responsavelNome;
    private String responsavelEmail;
    private Boolean responsavelAtivo;
    private Boolean responsavelPlatformAdmin;
    private Boolean hasResponsavel;
    private Boolean hasMembers;
    private Boolean hasOwnerMember;
    private Boolean hasActiveOwnerMember;
    private Boolean hasValidBusinessOwner;
    private Boolean hasPlatformAdminAsBusinessAccountOwner;
    private Long memberCount;
    private Long linkedTenantCount;
    private Long activeTenantCount;
    private Boolean hasActiveTenants;
    private Boolean canActivate;
    private Boolean canLinkActiveTenant;
    private Boolean canResetOwnerLogin;
    private Boolean requiresBackfill;
    private List<String> blockingReasons;
    private List<String> warnings;
    private List<OwnerItem> owners;
    private List<TenantItem> tenants;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OwnerItem {
        private Long userId;
        private String username;
        private String nome;
        private String email;
        private Boolean ativo;
        private Boolean platformAdmin;
        private BusinessAccountRole role;
        private BusinessAccountMemberEstado estado;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TenantItem {
        private Long tenantId;
        private String nome;
        private String slug;
        private String tenantCode;
        private TenantEstado estado;
        private Boolean hasTenantOwner;
        private Boolean hasPlatformAdminTenantOwner;
        private List<TenantOwnerItem> owners;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TenantOwnerItem {
        private Long userId;
        private String username;
        private String nome;
        private String email;
        private Boolean ativo;
        private Boolean platformAdmin;
        private TenantUserRole role;
        private TenantUserEstado estado;
    }
}
