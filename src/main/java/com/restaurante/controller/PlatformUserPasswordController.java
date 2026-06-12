package com.restaurante.controller;

import com.restaurante.dto.request.AdminResetPasswordRequest;
import com.restaurante.dto.response.AdminResetPasswordResponse;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.service.UserPasswordManagementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/users")
@RequiredArgsConstructor
@Tag(name = "Platform User Password", description = "Reset administrativo de credenciais de usuarios")
public class PlatformUserPasswordController {

    private final UserPasswordManagementService service;

    @PostMapping("/{userId}/password/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminResetPasswordResponse>> resetPassword(
            @PathVariable Long userId,
            @RequestBody(required = false) AdminResetPasswordRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Senha temporaria gerada",
                service.resetPasswordByPlatformAdmin(userId, request)
        ));
    }
}
