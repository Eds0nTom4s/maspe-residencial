package com.restaurante.controller;

import com.restaurante.dto.request.LoginAtendenteRequest;
import com.restaurante.dto.request.SelectTenantRequest;
import com.restaurante.dto.request.SolicitarOtpRequest;
import com.restaurante.dto.request.ValidarOtpRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.AuthResponse;
import com.restaurante.dto.response.AuthTenantOptionResponse;
import com.restaurante.dto.response.ClienteResponse;
import com.restaurante.dto.response.LoginAtendenteResponse;
import com.restaurante.dto.response.SelectTenantResponse;
import com.restaurante.service.AuthService;
import com.restaurante.service.ClienteService;
import com.restaurante.service.InstituicaoService;
import com.restaurante.service.TenantTokenService;
import com.restaurante.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    private final JwtTokenProvider jwtTokenProvider;
    private final InstituicaoService instituicaoService;
    private final TenantTokenService tenantTokenService;

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
    @Operation(summary = "Validar OTP", description = "Valida o código OTP e autentica o cliente. Retorna JWT.")
    public ResponseEntity<ApiResponse<AuthResponse>> validarOtp(@Valid @RequestBody ValidarOtpRequest request) {
        log.info("=== VALIDAR OTP ===");
        log.info("Telefone recebido: {}", request.getTelefone());
        log.info("Código OTP recebido: {}", request.getCodigo());
        
        // 1. Valida o OTP e busca o cliente
        ClienteResponse cliente = clienteService.validarOtp(request);
        
        // 2. Gera o Token JWT incluindo o nome da Instituição ativa
        String instNome = instituicaoService.getInstituicaoAtiva().getNome();
        String token = jwtTokenProvider.generateToken(cliente.getTelefone(), "ROLE_CLIENTE", instNome);
        
        // 3. Constrói o Response com dados de autenticação.
        AuthResponse authResponse = AuthResponse.builder()
            .username(cliente.getNome() != null ? cliente.getNome() : cliente.getTelefone())
            .accessToken(token)
            .tokenType("Bearer")
            .expiresIn(jwtTokenProvider.getExpirationMs() / 1000L)
            .build();
        
        log.info("OTP validado com sucesso para cliente ID: {}. Token emitido.", cliente.getId());
        return ResponseEntity.ok(ApiResponse.success("Autenticação realizada com sucesso", authResponse));
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

    /**
     * Seleciona um tenant e emite um token tenant-scoped para uso em /api/tenant/**.
     *
     * POST /api/auth/tenant/select
     */
    @PostMapping("/tenant/select")
    @Operation(summary = "Selecionar tenant", description = "Emite token TENANT com claims tenantId/tenantRoles.")
    public ResponseEntity<ApiResponse<SelectTenantResponse>> selectTenant(@Valid @RequestBody SelectTenantRequest request) {
        SelectTenantResponse resp = tenantTokenService.selectTenant(request);
        return ResponseEntity.ok(ApiResponse.success("Tenant selecionado", resp));
    }

    /**
     * Lista os tenants acessíveis ao usuário autenticado.
     *
     * GET /api/auth/tenants
     *
     * Segurança:
     * - Exige JWT global válido (não é endpoint público).
     * - Retorna SOMENTE tenants aos quais o usuário tem vínculo ativo.
     * - Sem token: 401. Token inválido/expirado: 401.
     * - Usuário sem tenants: 200 com lista vazia.
     * - Tenants inativos são filtrados pelo service.
     */
    @GetMapping("/tenants")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Listar tenants acessíveis",
            description = "Retorna os tenants ativos aos quais o usuário autenticado tem acesso. Exige JWT global.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<List<AuthTenantOptionResponse>>> listarTenantsAcessiveis() {
        log.info("GET /auth/tenants - listando tenants do usuário autenticado");
        List<AuthTenantOptionResponse> tenants = tenantTokenService.listarTenantsAcessiveis();
        log.info("GET /auth/tenants - retornando {} tenants", tenants.size());
        return ResponseEntity.ok(ApiResponse.success("Tenants disponíveis", tenants));
    }
}
