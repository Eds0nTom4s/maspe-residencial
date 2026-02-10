package com.restaurante.controller;

import com.restaurante.dto.request.CriarPagamentoRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.PagamentoResponse;
import com.restaurante.service.PagamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST para operações com Pagamento
 */
@RestController
@RequestMapping("/pagamentos")
@RequiredArgsConstructor
@Tag(name = "Pagamentos", description = "Endpoints para gestão de pagamentos")
public class PagamentoController {

    private final PagamentoService pagamentoService;

    /**
     * Cria um novo pagamento
     * POST /api/pagamentos
     */
    @PostMapping
    @Operation(summary = "Criar pagamento", description = "Cria um pagamento para uma mesa")
    public ResponseEntity<ApiResponse<PagamentoResponse>> criar(@Valid @RequestBody CriarPagamentoRequest request) {
        PagamentoResponse pagamento = pagamentoService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Pagamento criado com sucesso", pagamento));
    }

    /**
     * Busca pagamento por ID
     * GET /api/pagamentos/{id}
     */
    @GetMapping("/{id}")
    @Operation(summary = "Buscar pagamento por ID", description = "Retorna os detalhes de um pagamento")
    public ResponseEntity<ApiResponse<PagamentoResponse>> buscarPorId(@PathVariable Long id) {
        PagamentoResponse pagamento = pagamentoService.buscarPorId(id);
        return ResponseEntity.ok(ApiResponse.success("Pagamento encontrado", pagamento));
    }

    /**
     * Busca pagamento por unidade de consumo
     * GET /api/pagamentos/unidade-consumo/{unidadeConsumoId}
     */
    @GetMapping("/unidade-consumo/{unidadeConsumoId}")
    @Operation(summary = "Buscar pagamento da unidade de consumo", description = "Retorna o pagamento de uma unidade de consumo específica")
    public ResponseEntity<ApiResponse<PagamentoResponse>> buscarPorUnidadeConsumo(@PathVariable Long unidadeConsumoId) {
        PagamentoResponse pagamento = pagamentoService.buscarPorUnidadeConsumoId(unidadeConsumoId);
        return ResponseEntity.ok(ApiResponse.success("Pagamento encontrado", pagamento));
    }

    /**
     * Aprova um pagamento manualmente
     * PUT /api/pagamentos/{id}/aprovar
     */
    @PutMapping("/{id}/aprovar")
    @Operation(summary = "Aprovar pagamento", description = "Aprova um pagamento manualmente")
    public ResponseEntity<ApiResponse<PagamentoResponse>> aprovar(@PathVariable Long id) {
        PagamentoResponse pagamento = pagamentoService.aprovar(id);
        return ResponseEntity.ok(ApiResponse.success("Pagamento aprovado com sucesso", pagamento));
    }

    /**
     * Recusa um pagamento
     * PUT /api/pagamentos/{id}/recusar
     */
    @PutMapping("/{id}/recusar")
    @Operation(summary = "Recusar pagamento", description = "Recusa um pagamento")
    public ResponseEntity<ApiResponse<PagamentoResponse>> recusar(
            @PathVariable Long id,
            @RequestParam String motivo) {
        PagamentoResponse pagamento = pagamentoService.recusar(id, motivo);
        return ResponseEntity.ok(ApiResponse.success("Pagamento recusado", pagamento));
    }

    /**
     * Cancela um pagamento
     * PUT /api/pagamentos/{id}/cancelar
     */
    @PutMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar pagamento", description = "Cancela um pagamento")
    public ResponseEntity<ApiResponse<PagamentoResponse>> cancelar(
            @PathVariable Long id,
            @RequestParam String motivo) {
        PagamentoResponse pagamento = pagamentoService.cancelar(id, motivo);
        return ResponseEntity.ok(ApiResponse.success("Pagamento cancelado", pagamento));
    }

    /**
     * Webhook para receber notificações de gateway de pagamento
     * POST /api/pagamentos/webhook
     */
    @PostMapping("/webhook")
    @Operation(summary = "Webhook do gateway", description = "Recebe notificações do gateway de pagamento")
    public ResponseEntity<ApiResponse<Void>> webhook(
            @RequestParam String transactionId,
            @RequestParam String status) {
        // TODO: Adicionar autenticação do webhook
        pagamentoService.processarWebhook(transactionId, status, null);
        return ResponseEntity.ok(ApiResponse.success("Webhook processado", null));
    }
}
