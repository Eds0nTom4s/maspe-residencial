package com.restaurante.controller;

import com.restaurante.dto.request.LoginAtendenteRequest;
import com.restaurante.dto.request.SolicitarOtpRequest;
import com.restaurante.dto.request.ValidarOtpRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.ClienteResponse;
import com.restaurante.dto.response.LoginAtendenteResponse;
import com.restaurante.service.AuthService;
import com.restaurante.service.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST para operações de autenticação
 * - Clientes: autenticação via OTP
 * - Atendentes/Gerentes: autenticação via telefone + senha
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Endpoints para autenticação de clientes e atendentes")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final ClienteService clienteService;
    private final AuthService authService;

    /**
     * Solicita OTP para o telefone informado
     * POST /api/auth/solicitar-otp
     */
    @PostMapping("/solicitar-otp")
    @Operation(summary = "Solicitar OTP", description = "Envia código OTP para o telefone do cliente")
    public ResponseEntity<ApiResponse<Void>> solicitarOtp(@Valid @RequestBody SolicitarOtpRequest request) {
        log.info("=== SOLICITAR OTP ===");
        log.info("Telefone recebido: {}", request.getTelefone());
        log.info("Request completo: {}", request);
        
        clienteService.solicitarOtp(request);
        
        log.info("OTP enviado com sucesso para: {}", request.getTelefone());
        return ResponseEntity.ok(ApiResponse.success("OTP enviado com sucesso", null));
    }

    /**
     * Valida OTP e autentica o cliente
     * POST /api/auth/validar-otp
     */
    @PostMapping("/validar-otp")
    @Operation(summary = "Validar OTP", description = "Valida o código OTP e autentica o cliente")
    public ResponseEntity<ApiResponse<ClienteResponse>> validarOtp(@Valid @RequestBody ValidarOtpRequest request) {
        log.info("=== VALIDAR OTP ===");
        log.info("Telefone recebido: {}", request.getTelefone());
        log.info("Código OTP recebido: {}", request.getCodigo());
        log.info("Request completo: {}", request);
        
        ClienteResponse cliente = clienteService.validarOtp(request);
        
        log.info("OTP validado com sucesso para cliente ID: {}", cliente.getId());
        return ResponseEntity.ok(ApiResponse.success("Autenticação realizada com sucesso", cliente));
    }
    
    /**
     * Login para Atendente/Gerente com telefone e senha
     * POST /api/auth/admin/login
     */
    @PostMapping("/admin/login")
    @Operation(summary = "Login Atendente/Gerente", description = "Autentica atendente ou gerente com telefone e senha")
    public ResponseEntity<ApiResponse<LoginAtendenteResponse>> loginAdmin(@Valid @RequestBody LoginAtendenteRequest request) {
        log.info("=== LOGIN ADMIN (TELEFONE) ===");
        log.info("Telefone recebido: {}", request.getTelefone());
        log.info("Senha recebida: [OCULTA - {} caracteres]", request.getSenha() != null ? request.getSenha().length() : 0);
        log.info("Request completo (sem senha): telefone={}", request.getTelefone());
        
        LoginAtendenteResponse response = authService.loginAtendente(request);
        
        log.info("Login admin realizado com sucesso - ID: {}, Nome: {}, Tipo: {}", 
                 response.getId(), response.getNome(), response.getTipoUsuario());
        return ResponseEntity.ok(ApiResponse.success("Login realizado com sucesso", response));
    }
}

