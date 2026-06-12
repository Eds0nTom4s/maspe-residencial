package com.restaurante.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformTenantAccessResetPasswordResponse {
    private Long tenantId;
    private Long userId;
    private String username;
    private String temporaryPassword;
    private Boolean mustChangePassword;
    private LocalDateTime temporaryPasswordExpiresAt;
}
