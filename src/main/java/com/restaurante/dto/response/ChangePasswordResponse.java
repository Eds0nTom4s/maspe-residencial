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
public class ChangePasswordResponse {
    private Long userId;
    private String username;
    private Boolean mustChangePassword;
    private Boolean passwordResetRequired;
    private LocalDateTime lastPasswordChangedAt;
    private String message;
}
