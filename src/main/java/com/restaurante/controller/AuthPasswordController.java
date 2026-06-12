package com.restaurante.controller;

import com.restaurante.dto.request.ChangePasswordRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.ChangePasswordResponse;
import com.restaurante.service.UserPasswordManagementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/password")
@RequiredArgsConstructor
@Tag(name = "Auth Password", description = "Gestao de senha do usuario autenticado")
public class AuthPasswordController {

    private final UserPasswordManagementService service;

    @PostMapping("/change")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ChangePasswordResponse>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Senha alterada com sucesso", service.changeOwnPassword(request)));
    }
}
