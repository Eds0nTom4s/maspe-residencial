package com.restaurante.dto.response;

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
public class PlatformTenantAccessSummaryResponse {
    private Long tenantId;
    private String tenantNome;
    private String tenantSlug;
    private Long businessAccountId;
    private String businessAccountNome;
    private UserAccessSummary owner;
    private List<UserAccessSummary> users;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserAccessSummary {
        private Long userId;
        private String username;
        private String nome;
        private String email;
        private String telefone;
        private Long tenantUserId;
        private String tenantRole;
        private String tenantEstado;
        private Long businessAccountMemberId;
        private String businessAccountRole;
        private String businessAccountMemberEstado;
        private Boolean mustChangePassword;
        private Boolean passwordResetRequired;
        private LocalDateTime temporaryPasswordExpiresAt;
        private LocalDateTime lastPasswordChangedAt;
    }
}
