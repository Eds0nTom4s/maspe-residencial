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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Controller REST para Fundo de Consumo (pré-pago)
 *
 * <h2>Modelo de negócio</h2>
 * O Fundo de Consumo é um método de pagamento pré-pago identificado por
 * um QR Code único. O <b>token do QR Code é o identificador universal</b>
 * de qualquer operação — o garçom lê o QR Code e usa o token resultante
 * para carregar, debitar ou consultar o fundo, independentemente de o fundo
 * pertencer a um cliente identificado ou ser anónimo.
 *
 * <h2>Dois tipos de fundo — mesma API operacional</h2>
 * <ul>
 *   <li><b>Identificado</b> — associado a um {@code clienteId}. O sistema
 *       gera automaticamente um token QR único no momento da criação.</li>
 *   <li><b>Anónimo</b>     — sem cliente. O token é o QR Code da própria
 *       mesa ({@code UnidadeConsumo.qrCode}).</li>
 * </ul>
 * Após a criação, <b>ambos operam exclusivamente pelo token</b>.
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
    // CRIAÇÃO — dois pontos de entrada, depois tudo opera pelo token
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Cria fundo pré-pago para um cliente identificado.
     * O backend gera automaticamente o tokenPortador (UUID).
     * O admin imprime esse token como QR Code e entrega ao cliente.
     * Restrição: um cliente só pode ter 1 fundo ativo.
     *
     * POST /api/fundos/cliente/{clienteId}
     */
    @PostMapping("/cliente/{clienteId}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(
        summary = "Criar fundo para cliente",
        description = "Cria fundo pré-pago com saldo zero e gera token QR único automaticamente."
    )
    public ResponseEntity<ApiResponse<FundoConsumoResponse>> criarFundoCliente(
            @PathVariable Long clienteId) {

        log.info("Criando fundo de consumo para cliente {}", clienteId);
        FundoConsumo fundo = fundoConsumoService.criarFundo(clienteId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Fundo criado. Token QR: " + fundo.getTokenPortador(),
                        toResponse(fundo)));
    }

    /**
     * Cria fundo pré-pago anónimo vinculado ao QR Code da mesa.
     * O {@code token} deve ser o {@code qrCode} da UnidadeConsumo.
     * Esse mesmo token identifica o fundo em todas as operações seguintes.
     *
     * POST /api/fundos/anonimo/{token}
     */
    @PostMapping("/anonimo/{token}")
    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE', 'ADMIN')")
    @Operation(
        summary = "Criar fundo anónimo (QR Code da mesa)",
        description = "Cria fundo sem cliente. O token do QR Code da mesa torna-se o identificador do fundo."
    )
    public ResponseEntity<ApiResponse<FundoConsumoResponse>> criarFundoAnonimo(
            @PathVariable String token) {

        log.info("Criando fundo anónimo para token: {}", token);
        FundoConsumo fundo = fundoConsumoService.criarFundoAnonimo(token);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Fundo anónimo criado", toResponse(fundo)));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // OPERAÇÕES UNIVERSAIS — garçom lê QR → token → usa estes endpoints
    // Funcionam igualmente para fundos de clientes e anónimos
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
        description = "Retorna apenas o saldo disponível em AOA."
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

        log.info("Recarregando fundo (token: {}) — {} AOA", token, request.getValor());
        TransacaoFundo transacao = fundoConsumoService.recarregarPorToken(
                token, request.getValor(), request.getObservacoes());
        return ResponseEntity.ok(ApiResponse.success(
                "Recarga concluída. Novo saldo: " + transacao.getSaldoNovo() + " AOA",
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
    // LOOKUP ADMINISTRATIVO — busca por clienteId, retorna token para usar
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Busca o fundo de um cliente pelo ID do cliente.
     * Uso administrativo — a resposta inclui o tokenPortador
     * que o garçom deve usar nas operações de recarga/débito.
     *
     * GET /api/fundos/cliente/{clienteId}
     */
    @GetMapping("/cliente/{clienteId}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(
        summary = "Buscar fundo por clienteId (admin)",
        description = "Retorna o fundo do cliente, incluindo o tokenPortador (QR Code) a usar nas operações."
    )
    public ResponseEntity<ApiResponse<FundoConsumoResponse>> buscarPorCliente(
            @PathVariable Long clienteId) {

        log.info("Buscando fundo do cliente {}", clienteId);
        FundoConsumo fundo = fundoConsumoService.buscarPorCliente(clienteId);
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
                .clienteId(fundo.getCliente() != null ? fundo.getCliente().getId() : null)
                .tokenPortador(fundo.getTokenPortador())
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
