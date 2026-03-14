package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.model.entity.ConfiguracaoFinanceiraSistema;
import com.restaurante.service.ConfiguracaoFinanceiraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.stream.Collectors;

/**
 * Controller para gerenciamento de configurações financeiras do sistema
 * 
 * ⚠️ SEGURANÇA CRÍTICA: Apenas ADMIN pode alterar configurações financeiras
 * - Ativar/desativar pós-pago globalmente
 * - Alterar limite de risco de pós-pago
 * - Visualizar configurações atuais
 */
@RestController
@RequestMapping("/configuracoes-financeiras")
@RequiredArgsConstructor
@Tag(name = "Configurações Financeiras", description = "Gerenciamento de políticas financeiras do sistema")
@PreAuthorize("hasRole('ADMIN')")
public class ConfiguracaoFinanceiraController {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracaoFinanceiraController.class);

    private final ConfiguracaoFinanceiraService configuracaoFinanceiraService;

    /**
     * Busca configuração financeira atual
     * GET /api/configuracoes-financeiras
     */
    @GetMapping
    @Operation(summary = "Buscar configuração atual", description = "Retorna a configuração financeira vigente")
    public ResponseEntity<ApiResponse<ConfiguracaoFinanceiraSistema>> buscarConfiguracao() {
        log.info("Requisição para buscar configuração financeira");
        
        ConfiguracaoFinanceiraSistema config = configuracaoFinanceiraService.buscarOuCriarConfiguracao();
        return ResponseEntity.ok(ApiResponse.success("Configuração encontrada", config));
    }

    /**
     * Ativa pós-pago globalmente
     * PUT /api/configuracoes-financeiras/pos-pago/ativar
     *
     * @param motivo Motivo declarado (opcional, mas recomendado para compliance)
     */
    @PutMapping("/pos-pago/ativar")
    @Operation(summary = "Ativar pós-pago", description = "Ativa a modalidade pós-pago globalmente no sistema")
    public ResponseEntity<ApiResponse<Void>> ativarPosPago(
            @RequestParam(required = false) String motivo,
            Authentication authentication) {
        String username = authentication.getName();
        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        log.warn("🔐 OPERAÇÃO CRÍTICA: Ativando pós-pago globalmente - Usuário: {} | Roles: {}", username, roles);

        configuracaoFinanceiraService.ativarPosPago(username, roles, motivo);

        return ResponseEntity.ok(ApiResponse.success("Pós-pago ativado globalmente", null));
    }

    /**
     * Desativa pós-pago globalmente
     * PUT /api/configuracoes-financeiras/pos-pago/desativar
     *
     * @param motivo Motivo declarado (opcional, mas recomendado para compliance)
     */
    @PutMapping("/pos-pago/desativar")
    @Operation(summary = "Desativar pós-pago", description = "Desativa a modalidade pós-pago globalmente no sistema")
    public ResponseEntity<ApiResponse<Void>> desativarPosPago(
            @RequestParam(required = false) String motivo,
            Authentication authentication) {
        String username = authentication.getName();
        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        log.warn("🔐 OPERAÇÃO CRÍTICA: Desativando pós-pago globalmente - Usuário: {} | Roles: {}", username, roles);

        configuracaoFinanceiraService.desativarPosPago(username, roles, motivo);

        return ResponseEntity.ok(ApiResponse.success("Pós-pago desativado globalmente", null));
    }

    /**
     * Altera limite de pós-pago
     * PUT /api/configuracoes-financeiras/pos-pago/limite
     *
     * @param novoLimite Novo limite em AOA (mínimo 100.00)
     * @param motivo     Motivo da alteração (opcional)
     */
    @PutMapping("/pos-pago/limite")
    @Operation(summary = "Alterar limite de pós-pago", description = "Define novo limite de risco para pós-pago por unidade de consumo")
    public ResponseEntity<ApiResponse<Void>> alterarLimitePosPago(
            @RequestParam BigDecimal novoLimite,
            @RequestParam(required = false) String motivo,
            Authentication authentication) {

        String username = authentication.getName();
        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        log.warn("🔐 OPERAÇÃO CRÍTICA: Alterando limite de pós-pago para {} AOA - Usuário: {} | Roles: {}",
                novoLimite, username, roles);

        if (novoLimite.compareTo(new BigDecimal("100.00")) < 0) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Limite mínimo é 100.00 AOA"));
        }

        configuracaoFinanceiraService.alterarLimitePosPago(novoLimite, username, roles, motivo);

        return ResponseEntity.ok(
                ApiResponse.success("Limite de pós-pago alterado para " + novoLimite + " AOA", null));
    }

    /**
     * Altera valor mínimo de operação financeira
     * PUT /api/configuracoes-financeiras/valor-minimo
     *
     * @param novoValor Novo valor mínimo em AOA
     * @param motivo    Motivo da alteração (opcional)
     */
    @PutMapping("/valor-minimo")
    @Operation(summary = "Alterar valor mínimo de operação",
               description = "Define novo valor mínimo para recargas, débitos e estornos")
    public ResponseEntity<ApiResponse<Void>> alterarValorMinimo(
            @RequestParam BigDecimal novoValor,
            @RequestParam(required = false) String motivo,
            Authentication authentication) {

        String username = authentication.getName();
        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        log.warn("🔐 OPERAÇÃO CRÍTICA: Alterando valor mínimo para {} AOA - Usuário: {}", novoValor, username);
        configuracaoFinanceiraService.alterarValorMinimo(novoValor, username, roles, motivo);
        return ResponseEntity.ok(
                ApiResponse.success("Valor mínimo alterado para " + novoValor + " AOA", null));
    }

    /**
     * Verifica status do pós-pago
     * GET /api/configuracoes-financeiras/pos-pago/status
     *
     * Endpoint público para validação rápida (não expõe dados sensíveis)
     */
    @GetMapping("/pos-pago/status")
    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE', 'ADMIN')")
    @Operation(summary = "Verificar status pós-pago", description = "Verifica se pós-pago está ativo no sistema")
    public ResponseEntity<ApiResponse<Boolean>> verificarStatusPosPago() {
        boolean ativo = configuracaoFinanceiraService.isPosPagoAtivo();
        return ResponseEntity.ok(ApiResponse.success("Status do pós-pago", ativo));
    }
}
