package com.restaurante.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformTenantAccessResetPasswordRequest {
    private Long userId;
    private String motivo;
}
