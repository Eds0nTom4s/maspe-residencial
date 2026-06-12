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
public class AdminResetPasswordResponse {
    private Long userId;
    private String username;
    private String temporaryPassword;
    private LocalDateTime temporaryPasswordExpiresAt;
    private Boolean mustChangePassword;
    private Boolean passwordResetRequired;
    private LocalDateTime resetAt;
}
