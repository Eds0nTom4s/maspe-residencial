package com.restaurante.controller;

import com.restaurante.dto.request.IniciarPagamentoRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.PagamentoGatewayResponse;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.enums.TipoPagamentoFinanceiro;
import com.restaurante.financeiro.service.PagamentoGatewayService;
import com.restaurante.model.entity.Pagamento;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller REST para pagamentos via gateway AppyPay.
 *
 * <h2>Responsabilidade</h2>
 * Expõe operações para <b>iniciar</b> e <b>consultar</b> pagamentos de recarga
 * de Fundo de Consumo através do gateway AppyPay (métodos GPO e REF).
 *
 * <h2>Nota sobre callbacks</h2>
 * A confirmação automática de pagamentos REF é feita pelo AppyPay via
 * {@code POST /api/pagamentos/callback} — ver {@code PagamentoCallbackController}.
 *
 * <h2>Base URL</h2>
 * {@code /api/pagamentos}
 */
@RestController
@RequestMapping("/pagamentos")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Pagamentos", description = "Pagamentos AppyPay — recargas de Fundo de Consumo")
public class PagamentoController {

    private final PagamentoGatewayService pagamentoGatewayService;

    // ═══════════════════════════════════════════════════════════════════════
    // INICIAR PAGAMENTO
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Inicia um pagamento AppyPay para recarregar um Fundo de Consumo.
     *
     * <h3>Fluxo GPO (instantâneo)</h3>
     * O cliente paga via app AppyPay → saldo creditado imediatamente.
     * A resposta já terá {@code status: CONFIRMADO}.
     *
     * <h3>Fluxo REF (referência bancária)</h3>
     * A resposta contém {@code entidade} + {@code referencia} para pagamento
     * em Multicaixa/ATM. O saldo é creditado após callback da AppyPay.
     * A resposta inicial terá {@code status: PENDENTE}.
     *
     * POST /api/pagamentos/recarregar
     * Body: { "fundoId": 1, "valor": 5000.00, "metodo": "REF" }
     */
    @PostMapping("/recarregar")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(
        summary = "Iniciar recarga de fundo via AppyPay",
        description = "Cria cobrança no gateway AppyPay. GPO: confirmação imediata. REF: aguarda pagamento bancário."
    )
    public ResponseEntity<ApiResponse<PagamentoGatewayResponse>> recarregarFundo(
            @Valid @RequestBody IniciarPagamentoRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        String usuario = authentication.getName();
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.replace("ROLE_", ""))
                .findFirst().orElse("UNKNOWN");
        String ip = httpRequest.getRemoteAddr();

        log.info("Iniciando pagamento AppyPay: fundoId={}, valor={}, metodo={}, usuario={}",
                request.getFundoId(), request.getValor(), request.getMetodo(), usuario);

        Pagamento pagamento = pagamentoGatewayService.criarPagamentoRecargaFundo(
                request.getFundoId(),
                request.getValor(),
                request.getMetodo(),
                usuario,
                role,
                ip,
                request.getTelefone());

        PagamentoGatewayResponse resposta = toResponse(pagamento);

        String mensagem = pagamento.isConfirmado()
                ? "Pagamento GPO confirmado. Saldo creditado."
                : "Referência gerada. Entidade: " + pagamento.getEntidade()
                  + " | Ref: " + pagamento.getReferencia()
                  + " | Valor: " + com.restaurante.util.MoneyFormatter.format(pagamento.getAmount());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(mensagem, resposta));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CONSULTAS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Consulta o estado de um pagamento pelo ID.
     * Útil para verificar se um REF já foi confirmado.
     *
     * GET /api/pagamentos/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE', 'ADMIN')")
    @Operation(
        summary = "Consultar pagamento por ID",
        description = "Retorna estado actual do pagamento. Para REF, verifique se status mudou para CONFIRMADO."
    )
    public ResponseEntity<ApiResponse<PagamentoGatewayResponse>> buscarPorId(@PathVariable Long id) {
        log.info("Consultando pagamento ID: {}", id);
        Pagamento pagamento = pagamentoGatewayService.buscarPorId(id);
        return ResponseEntity.ok(ApiResponse.success("Pagamento encontrado", toResponse(pagamento)));
    }

    /**
     * Lista o histórico de pagamentos AppyPay de um Fundo de Consumo.
     * Inclui recargas confirmadas, pendentes e falhadas.
     *
     * GET /api/pagamentos/fundo/{fundoId}
     */
    @GetMapping("/fundo/{fundoId}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(
        summary = "Histórico de pagamentos de um fundo",
        description = "Lista todas as tentativas de recarga AppyPay do fundo, mais recentes primeiro."
    )
    public ResponseEntity<ApiResponse<List<PagamentoGatewayResponse>>> listarPorFundo(
            @PathVariable Long fundoId) {

        log.info("Histórico de pagamentos do fundo: {}", fundoId);
        List<PagamentoGatewayResponse> lista = pagamentoGatewayService.listarPorFundo(fundoId)
                .stream().map(this::toResponse).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(lista.size() + " pagamento(s) encontrado(s)", lista));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Mapper interno
    // ═══════════════════════════════════════════════════════════════════════

    private PagamentoGatewayResponse toResponse(Pagamento p) {
        return PagamentoGatewayResponse.builder()
                .id(p.getId())
                .fundoConsumoId(p.getFundoConsumo() != null ? p.getFundoConsumo().getId() : null)
                .pedidoId(p.getPedido() != null ? p.getPedido().getId() : null)
                .tipoPagamento(p.getTipoPagamento())
                .metodo(p.getMetodo())
                .amount(p.getAmount())
                .status(p.getStatus())
                .externalReference(p.getExternalReference())
                .entidade(p.getEntidade())
                .referencia(p.getReferencia())
                .confirmedAt(p.getConfirmedAt())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
