package com.restaurante.dto.response;

import java.time.LocalDateTime;

public record ChangePasswordResponse(
        Long userId,
        String username,
        Boolean mustChangePassword,
        Boolean passwordResetRequired,
        LocalDateTime lastPasswordChangedAt,
        String accessToken,
        String tokenType,
        Long expiresIn
) {}
