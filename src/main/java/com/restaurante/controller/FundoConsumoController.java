package com.restaurante.controller;

import com.restaurante.dto.request.RecarregarFundoRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.FundoConsumoResponse;
import com.restaurante.dto.response.TransacaoFundoResponse;
import com.restaurante.model.entity.FundoConsumo;
import com.restaurante.model.entity.TransacaoFundo;
import com.restaurante.service.FundoConsumoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Controller REST para Fundo de Consumo (pré-pago)
 *
 * <h2>Modelo de negócio</h2>
 * O Fundo de Consumo é criado <b>automaticamente</b> quando uma SessaoConsumo é aberta.
 * O <b>qrCodeSessao</b> (UUID gerado na sessão) é o identificador universal
 * de qualquer operação — o garçom lê o QR Code e usa o token resultante
 * para carregar, consultar ou encerrar o fundo.
 *
 * <h2>Base URL</h2>
 * {@code /api/fundos}
 */
@RestController
@RequestMapping("/fundos")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Fundo de Consumo", description = "Gestão de fundos pré-pagos — identificados pelo QR Code (token)")
public class FundoConsumoController {

    private final FundoConsumoService fundoConsumoService;

    // ═══════════════════════════════════════════════════════════════════════
    // OPERAÇÕES — QR Code da sessão (qrCodeSessao) como identificador
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Consulta fundo pelo token do QR Code.
     * Retorna saldo, estado, clienteId (se identificado) e metadados.
     *
     * GET /api/fundos/{token}
     */
    @GetMapping("/{token}")
    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE', 'ADMIN')")
    @Operation(
        summary = "Consultar fundo (QR Code)",
        description = "Retorna saldo e dados. Funciona para fundos de clientes e anónimos."
    )
    public ResponseEntity<ApiResponse<FundoConsumoResponse>> consultar(
            @PathVariable String token) {

        log.info("Consultando fundo pelo token: {}", token);
        FundoConsumo fundo = fundoConsumoService.buscarPorToken(token);
        return ResponseEntity.ok(ApiResponse.success("Fundo encontrado", toResponse(fundo)));
    }

    /**
     * Retorna apenas o saldo disponível — resposta simplificada.
     * Útil para validação rápida antes de criar pedido.
     *
     * GET /api/fundos/{token}/saldo
     */
    @GetMapping("/{token}/saldo")
    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE', 'ADMIN')")
    @Operation(
        summary = "Consultar saldo (QR Code)",
        description = "Retorna apenas o saldo disponível na moeda configurada."
    )
    public ResponseEntity<ApiResponse<BigDecimal>> consultarSaldo(
            @PathVariable String token) {

        BigDecimal saldo = fundoConsumoService.consultarSaldoPorToken(token);
        return ResponseEntity.ok(ApiResponse.success("Saldo consultado", saldo));
    }

    /**
     * Recarrega (credita) saldo no fundo identificado pelo QR Code.
     * O garçom lê o QR Code do cliente/mesa e efectua a recarga.
     * Valor mínimo: ver {@code GET /api/configuracoes-financeiras} → {@code valorMinimoOperacao}.
     *
     * POST /api/fundos/{token}/recarregar
     * Body: { "valor": 5000.00, "observacoes": "Recarga balcão" }
     */
    @PostMapping("/{token}/recarregar")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(
        summary = "Recarregar fundo (QR Code)",
        description = "Credita valor no fundo. Requer GERENTE ou ADMIN."
    )
    public ResponseEntity<ApiResponse<TransacaoFundoResponse>> recarregar(
            @PathVariable String token,
            @Valid @RequestBody RecarregarFundoRequest request) {

        log.info("Recarregando fundo (token: {}) — {}", token, com.restaurante.util.MoneyFormatter.format(request.getValor()));
        TransacaoFundo transacao = fundoConsumoService.recarregarPorToken(
                token, request.getValor(), request.getObservacoes());
        return ResponseEntity.ok(ApiResponse.success(
                "Recarga concluída. Novo saldo: " + com.restaurante.util.MoneyFormatter.format(transacao.getSaldoNovo()),
                toTransacaoResponse(transacao)));
    }

    /**
     * Recarrega (credita) saldo na própria sessão ativa do cliente logado.
     * Endpoint exclusivo para auto-serviço (App do Cliente).
     *
     * POST /api/fundos/cliente/recarregar
     * Body: { "valor": 5000.00, "observacoes": "Recarga Multicaixa" }
     */
    @PostMapping("/cliente/recarregar")
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(
        summary = "Recarregar fundo próprio (Cliente)",
        description = "Credita valor no fundo da sessão ativa do cliente autenticado."
    )
    public ResponseEntity<ApiResponse<TransacaoFundoResponse>> recarregarCliente(
            @Valid @RequestBody RecarregarFundoRequest request) {

        log.info("Cliente solicitou auto-recarga — {}", com.restaurante.util.MoneyFormatter.format(request.getValor()));
        Long clienteId = com.restaurante.security.SecurityUtils.getUsuarioLogadoId();
        
        TransacaoFundo transacao = fundoConsumoService.recarregarCliente(
                clienteId, request.getValor(), request.getObservacoes());
                
        return ResponseEntity.ok(ApiResponse.success(
                "Recarga concluída com sucesso. Novo saldo: " + com.restaurante.util.MoneyFormatter.format(transacao.getSaldoNovo()),
                toTransacaoResponse(transacao)));
    }

    /**
     * Lista o histórico de movimentações do fundo (paginado, desc por data).
     *
     * GET /api/fundos/{token}/historico?page=0&size=20
     */
    @GetMapping("/{token}/historico")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(
        summary = "Histórico de transações (QR Code)",
        description = "Lista créditos, débitos e estornos. Paginado, mais recente primeiro."
    )
    public ResponseEntity<ApiResponse<Page<TransacaoFundoResponse>>> historico(
            @PathVariable String token,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Histórico do fundo (token: {}) — page={}, size={}", token, page, size);
        FundoConsumo fundo = fundoConsumoService.buscarPorToken(token);
        Pageable pageable = PageRequest.of(page, size);
        Page<TransacaoFundo> resultado = fundoConsumoService.buscarHistoricoPorFundo(fundo.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success("Histórico recuperado",
                resultado.map(this::toTransacaoResponse)));
    }

    /**
     * Encerra (desactiva) o fundo. Após encerrado não aceita débitos nem recargas.
     *
     * DELETE /api/fundos/{token}
     */
    @DeleteMapping("/{token}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Encerrar fundo (QR Code)",
        description = "Desactiva o fundo permanentemente. Apenas ADMIN."
    )
    public ResponseEntity<ApiResponse<Void>> encerrar(@PathVariable String token) {
        log.warn("Encerrando fundo (token: {})", token);
        fundoConsumoService.encerrarFundoPorToken(token);
        return ResponseEntity.ok(ApiResponse.success("Fundo encerrado", null));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LOOKUP ADMINISTRATIVO — busca por sessaoId, retorna qrCodeSessao
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Busca o fundo associado a uma sessão de consumo.
     * Uso administrativo — a resposta inclui o qrCodeSessao
     * que o garçom deve usar nas operações de recarga/débito.
     *
     * GET /api/fundos/sessao/{sessaoId}
     */
    @GetMapping("/sessao/{sessaoId}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(
        summary = "Buscar fundo por sessãoId (admin)",
        description = "Retorna o fundo da sessão, incluindo o qrCodeSessao a usar nas operações."
    )
    public ResponseEntity<ApiResponse<FundoConsumoResponse>> buscarPorSessao(
            @PathVariable Long sessaoId) {

        log.info("Buscando fundo da sessão {}", sessaoId);
        FundoConsumo fundo = fundoConsumoService.buscarPorSessaoId(sessaoId);
        return ResponseEntity.ok(ApiResponse.success("Fundo encontrado", toResponse(fundo)));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Mappers internos
    // ═══════════════════════════════════════════════════════════════════════

    private FundoConsumoResponse toResponse(FundoConsumo fundo) {
        return FundoConsumoResponse.builder()
                .id(fundo.getId())
                .saldoAtual(fundo.getSaldoAtual())
                .ativo(fundo.getAtivo())
                .sessaoId(fundo.getSessaoConsumo().getId())
                .qrCodeSessao(fundo.getSessaoConsumo().getQrCodeSessao())
                .createdAt(fundo.getCreatedAt())
                .updatedAt(fundo.getUpdatedAt())
                .build();
    }

    private TransacaoFundoResponse toTransacaoResponse(TransacaoFundo t) {
        return TransacaoFundoResponse.builder()
                .id(t.getId())
                .tipo(t.getTipo())
                .valor(t.getValor())
                .saldoAnterior(t.getSaldoAnterior())
                .saldoNovo(t.getSaldoNovo())
                .pedidoId(t.getPedido() != null ? t.getPedido().getId() : null)
                .observacoes(t.getObservacoes())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
