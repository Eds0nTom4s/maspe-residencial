package com.restaurante.controller;

import com.restaurante.dto.request.SolicitarOtpRequest;
import com.restaurante.dto.request.ValidarOtpRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.ClienteResponse;
import com.restaurante.service.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST para operações de autenticação de Cliente
 * Endpoints públicos para acesso via QR Code
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Endpoints para autenticação de clientes via OTP")
public class AuthController {

    private final ClienteService clienteService;

    /**
     * Solicita OTP para o telefone informado
     * POST /api/auth/solicitar-otp
     */
    @PostMapping("/solicitar-otp")
    @Operation(summary = "Solicitar OTP", description = "Envia código OTP para o telefone do cliente")
    public ResponseEntity<ApiResponse<Void>> solicitarOtp(@Valid @RequestBody SolicitarOtpRequest request) {
        clienteService.solicitarOtp(request);
        return ResponseEntity.ok(ApiResponse.success("OTP enviado com sucesso", null));
    }

    /**
     * Valida OTP e autentica o cliente
     * POST /api/auth/validar-otp
     */
    @PostMapping("/validar-otp")
    @Operation(summary = "Validar OTP", description = "Valida o código OTP e autentica o cliente")
    public ResponseEntity<ApiResponse<ClienteResponse>> validarOtp(@Valid @RequestBody ValidarOtpRequest request) {
        ClienteResponse cliente = clienteService.validarOtp(request);
        return ResponseEntity.ok(ApiResponse.success("Autenticação realizada com sucesso", cliente));
    }
}
