package com.restaurante.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminResetPasswordRequest {
    private String reason;
    private Integer temporaryPasswordExpiresInHours;
    private Boolean forceChangePassword;
}
