package com.restaurante.controller;

import com.restaurante.dto.request.AbrirSessaoRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.SessaoConsumoResponse;
import com.restaurante.service.SessaoConsumoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller para gerenciamento de Sessões de Consumo.
 *
 * <p>SessaoConsumo é o evento temporal de ocupação de uma mesa.
 * Uma sessão é criada ao sentar clientes e encerrada ao fechar a conta.
 *
 * <p>Roles:
 * <ul>
 *   <li>POST /sessoes-consumo                → ATENDENTE, GERENTE, ADMIN</li>
 *   <li>PUT  /sessoes-consumo/{id}/fechar    → ATENDENTE, GERENTE, ADMIN</li>
 *   <li>PUT  /sessoes-consumo/{id}/aguardar-pagamento → ATENDENTE, GERENTE, ADMIN</li>
 *   <li>GET  /sessoes-consumo/**             → autenticado</li>
 * </ul>
 */
@RestController
@RequestMapping("/sessoes-consumo")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Sessões de Consumo", description = "Ciclo de vida das sessões (evento temporal de ocupação de mesa)")
public class SessaoConsumoController {

    private final SessaoConsumoService sessaoConsumoService;

    // ──────────────────────────────────────────────────────────────────────────
    // Operações de ciclo de vida (Atendentes / Admin)
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Abrir nova sessão de consumo em uma mesa disponível")
    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE', 'ADMIN')")
    public ResponseEntity<ApiResponse<SessaoConsumoResponse>> abrir(
            @Valid @RequestBody AbrirSessaoRequest request) {
        log.info("POST /sessoes-consumo — mesaId={}", request.getMesaId());
        SessaoConsumoResponse response = sessaoConsumoService.abrir(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Sessão de consumo aberta com sucesso", response));
    }

    @PutMapping("/{id}/fechar")
    @Operation(summary = "Encerrar sessão de consumo — mesa fica DISPONÍVEL automaticamente")
    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE', 'ADMIN')")
    public ResponseEntity<ApiResponse<SessaoConsumoResponse>> fechar(@PathVariable Long id) {
        log.info("PUT /sessoes-consumo/{}/fechar", id);
        SessaoConsumoResponse response = sessaoConsumoService.fechar(id);
        return ResponseEntity.ok(ApiResponse.success("Sessão de consumo encerrada com sucesso", response));
    }

    @PutMapping("/{id}/aguardar-pagamento")
    @Operation(summary = "Sinalizar sessão para aguardar pagamento")
    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE', 'ADMIN')")
    public ResponseEntity<ApiResponse<SessaoConsumoResponse>> aguardarPagamento(@PathVariable Long id) {
        log.info("PUT /sessoes-consumo/{}/aguardar-pagamento", id);
        SessaoConsumoResponse response = sessaoConsumoService.aguardarPagamento(id);
        return ResponseEntity.ok(ApiResponse.success("Sessão aguardando pagamento", response));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Operações para Cliente (QR Ordering)
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/cliente/qr/{token}")
    @Operation(summary = "Cliente entra na sessão via QR Code da Mesa")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<ApiResponse<SessaoConsumoResponse>> entrarViaQr(@PathVariable String token) {
        String telefone = getUsuarioLogado();
        log.info("POST /sessoes-consumo/cliente/qr/{} — cliente: {}", token, telefone);
        
        SessaoConsumoResponse response = sessaoConsumoService.entrarViaQr(token, telefone);
        return ResponseEntity.ok(ApiResponse.success("Sessão validada/aberta com sucesso", response));
    }

    @GetMapping("/cliente/minha-sessao")
    @Operation(summary = "Buscar sessão ativa do cliente logado")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<ApiResponse<SessaoConsumoResponse>> buscarMinhaSessao() {
        String telefone = getUsuarioLogado();
        log.info("GET /sessoes-consumo/cliente/minha-sessao — cliente: {}", telefone);
        
        SessaoConsumoResponse response = sessaoConsumoService.buscarMinhaSessao(telefone);
        return ResponseEntity.ok(ApiResponse.success("Sessão ativa encontrada", response));
    }

    @PutMapping("/cliente/minha-sessao/aguardar-pagamento")
    @Operation(summary = "Cliente solicita a conta (fecha a mesa temporariamente para pagamento)")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<ApiResponse<SessaoConsumoResponse>> pedirConta() {
        String telefone = getUsuarioLogado();
        log.info("PUT /sessoes-consumo/cliente/minha-sessao/aguardar-pagamento — cliente: {}", telefone);
        
        SessaoConsumoResponse sessao = sessaoConsumoService.buscarMinhaSessao(telefone);
        SessaoConsumoResponse response = sessaoConsumoService.aguardarPagamento(sessao.getId());
        return ResponseEntity.ok(ApiResponse.success("Conta solicitada com sucesso", response));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Consultas
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(summary = "Buscar sessão por ID")
    public ResponseEntity<ApiResponse<SessaoConsumoResponse>> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Sucesso", sessaoConsumoService.buscarPorId(id)));
    }

    @GetMapping("/mesa/{mesaId}/ativa")
    @Operation(summary = "Buscar sessão ABERTA de uma mesa")
    public ResponseEntity<ApiResponse<SessaoConsumoResponse>> buscarSessaoAbertaDaMesa(
            @PathVariable Long mesaId) {
        return ResponseEntity.ok(ApiResponse.success("Sucesso",
                sessaoConsumoService.buscarSessaoAbertaDaMesa(mesaId)));
    }

    @GetMapping("/mesa/{mesaId}/historico")
    @Operation(summary = "Histórico completo de sessões de uma mesa (auditável)")
    public ResponseEntity<ApiResponse<List<SessaoConsumoResponse>>> listarPorMesa(
            @PathVariable Long mesaId) {
        return ResponseEntity.ok(ApiResponse.success("Sucesso",
                sessaoConsumoService.listarPorMesa(mesaId)));
    }

    @GetMapping("/abertas")
    @Operation(summary = "Listar todas as sessões atualmente abertas")
    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<SessaoConsumoResponse>>> listarAbertas() {
        return ResponseEntity.ok(ApiResponse.success("Sucesso", sessaoConsumoService.listarAbertas()));
    }

    private String getUsuarioLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
