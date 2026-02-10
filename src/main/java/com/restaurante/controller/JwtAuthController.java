package com.restaurante.controller;

import com.restaurante.dto.request.LoginRequest;
import com.restaurante.dto.request.RefreshTokenRequest;
import com.restaurante.dto.request.RegisterRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.AuthResponse;
import com.restaurante.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller para autenticação JWT (Staff: Atendente, Gerente, Cozinha, Admin)
 */
@RestController
@RequestMapping("/api/auth/jwt")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Autenticação JWT", description = "Endpoints de autenticação JWT para staff")
public class JwtAuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Realizar login com JWT")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("Requisição de login JWT para usuário: {}", request.getUsername());
        
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login realizado com sucesso", response));
    }

    @PostMapping("/register")
    @Operation(summary = "Registrar novo usuário staff")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Requisição de registro JWT para usuário: {}", request.getUsername());
        
        AuthResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Usuário registrado com sucesso", response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar access token usando refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("Requisição de renovação de token JWT");
        
        AuthResponse response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success("Token renovado com sucesso", response));
    }
}
