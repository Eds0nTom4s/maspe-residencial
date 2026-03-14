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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller para autenticação JWT (Staff: Atendente, Gerente, Cozinha, Admin)
 */
@RestController
@RequestMapping("/auth/jwt")
@Tag(name = "Autenticação JWT", description = "Endpoints de autenticação JWT para staff")
public class JwtAuthController {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthController.class);

    private final AuthService authService;

    public JwtAuthController(AuthService authService) {
        this.authService = authService;
    }
    @PostMapping("/login")
    @Operation(summary = "Realizar login com JWT")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("=== LOGIN JWT (USERNAME) ===");
        log.info("Username recebido: {}", request.getUsername());
        log.info("Password recebido: [OCULTA - {} caracteres]", request.getPassword() != null ? request.getPassword().length() : 0);
        log.info("Request completo (sem senha): username={}", request.getUsername());
        
        AuthResponse response = authService.login(request);
        
        log.info("Login JWT realizado com sucesso - Username: {}, Roles: {}", 
                 response.getUsername(), response.getRoles());
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
