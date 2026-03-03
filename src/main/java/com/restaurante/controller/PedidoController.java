package com.restaurante.controller;

import com.restaurante.dto.request.CriarPedidoRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.PedidoResponse;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.service.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST para operações com Pedido
 */
@RestController
@RequestMapping("/pedidos")
@RequiredArgsConstructor
@Tag(name = "Pedidos", description = "Endpoints para gestão de pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    /**
     * Cria um novo pedido
     * POST /api/pedidos
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE', 'ADMIN')")
    @Operation(summary = "Criar pedido", description = "Cria um novo pedido para uma mesa")
    public ResponseEntity<ApiResponse<PedidoResponse>> criar(@Valid @RequestBody CriarPedidoRequest request) {
        PedidoResponse pedido = pedidoService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Pedido criado com sucesso", pedido));
    }

    /**
     * Busca pedido por ID
     * GET /api/pedidos/{id}
     */
    @GetMapping("/{id}")
    @Operation(summary = "Buscar pedido por ID", description = "Retorna os detalhes de um pedido específico")
    public ResponseEntity<ApiResponse<PedidoResponse>> buscarPorId(@PathVariable Long id) {
        PedidoResponse pedido = pedidoService.buscarPorIdComResponse(id);
        return ResponseEntity.ok(ApiResponse.success("Pedido encontrado", pedido));
    }

    /**
     * Busca pedido por número
     * GET /api/pedidos/numero/{numero}
     */
    @GetMapping("/numero/{numero}")
    @Operation(summary = "Buscar pedido por número", description = "Busca pedido pelo número único")
    public ResponseEntity<ApiResponse<PedidoResponse>> buscarPorNumero(@PathVariable String numero) {
        PedidoResponse pedido = pedidoService.buscarPorNumero(numero);
        return ResponseEntity.ok(ApiResponse.success("Pedido encontrado", pedido));
    }

    /**
     * Lista pedidos por status
     * GET /api/pedidos/status/{status}
     */
    @GetMapping("/status/{status}")
    @Operation(summary = "Listar pedidos por status", description = "Lista pedidos filtrados por status")
    public ResponseEntity<ApiResponse<List<PedidoResponse>>> listarPorStatus(@PathVariable StatusPedido status) {
        List<PedidoResponse> pedidos = pedidoService.listarPorStatus(status);
        return ResponseEntity.ok(ApiResponse.success("Pedidos listados com sucesso", pedidos));
    }

    /**
     * Lista pedidos ativos (para painel do atendente)
     * GET /api/pedidos/ativos
     */
    @GetMapping("/ativos")
    @Operation(summary = "Listar pedidos ativos", description = "Lista pedidos pendentes, recebidos e em preparo")
    public ResponseEntity<ApiResponse<List<PedidoResponse>>> listarAtivos() {
        List<PedidoResponse> pedidos = pedidoService.listarPedidosAtivos();
        return ResponseEntity.ok(ApiResponse.success("Pedidos ativos listados", pedidos));
    }

    /**
     * Lista todos os pedidos de uma SessaoConsumo.
     * GET /api/pedidos/sessao-consumo/{id}
     */
    @GetMapping("/sessao-consumo/{id}")
    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE', 'ADMIN')")
    @Operation(summary = "Listar pedidos por sessão", description = "Lista todos os pedidos de uma sessão de consumo")
    public ResponseEntity<ApiResponse<List<PedidoResponse>>> listarPorSessaoConsumo(@PathVariable Long id) {
        List<PedidoResponse> pedidos = pedidoService.listarPorSessaoConsumo(id);
        return ResponseEntity.ok(ApiResponse.success("Pedidos da sessão listados", pedidos));
    }

    /**
     * Lista apenas pedidos activos (CRIADO ou EM_ANDAMENTO) de uma SessaoConsumo.
     * GET /api/pedidos/sessao-consumo/{id}/ativo
     */
    @GetMapping("/sessao-consumo/{id}/ativo")
    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE', 'ADMIN')")
    @Operation(summary = "Pedidos activos da sessão", description = "Lista pedidos CRIADO ou EM_ANDAMENTO de uma sessão")
    public ResponseEntity<ApiResponse<List<PedidoResponse>>> listarAtivosPorSessaoConsumo(@PathVariable Long id) {
        List<PedidoResponse> pedidos = pedidoService.listarAtivosPorSessaoConsumo(id);
        return ResponseEntity.ok(ApiResponse.success("Pedidos activos da sessão listados", pedidos));
    }

    /**
     * Cancela um pedido
     * PUT /api/pedidos/{id}/cancelar
     *
     * @param motivo Motivo obrigatório do cancelamento
     */
    @PutMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(summary = "Cancelar pedido", description = "Cancela um pedido com motivo obrigatório")
    public ResponseEntity<ApiResponse<PedidoResponse>> cancelar(
            @PathVariable Long id,
            @RequestParam String motivo) {
        PedidoResponse pedido = pedidoService.cancelar(id, motivo);
        return ResponseEntity.ok(ApiResponse.success("Pedido cancelado com sucesso", pedido));
    }

    /**
     * Confirma o pedido — transita CRIADO → EM_ANDAMENTO, tornando os sub-pedidos
     * visíveis para as cozinhas.
     * PUT /api/pedidos/{id}/confirmar
     */
    @PutMapping("/{id}/confirmar")
    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE', 'ADMIN')")
    @Operation(
        summary = "Confirmar pedido",
        description = "Transita sub-pedidos de CRIADO para PENDENTE, tornando-os visíveis nas cozinhas."
    )
    public ResponseEntity<ApiResponse<PedidoResponse>> confirmar(@PathVariable Long id) {
        PedidoResponse pedido = pedidoService.confirmar(id);
        return ResponseEntity.ok(ApiResponse.success("Pedido confirmado", pedido));
    }

    /**
     * Confirma manualmente o pagamento de um pedido pós-pago.
     * Apenas GERENTE e ADMIN.
     * PUT /api/pedidos/{id}/confirmar-pagamento
     */
    @PutMapping("/{id}/confirmar-pagamento")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(
        summary = "Confirmar pagamento pós-pago",
        description = "Marca pedido POS_PAGO como PAGO. Usar quando cliente paga em dinheiro ou por outro meio."
    )
    public ResponseEntity<ApiResponse<PedidoResponse>> confirmarPagamento(@PathVariable Long id) {
        PedidoResponse pedido = pedidoService.confirmarPagamento(id);
        return ResponseEntity.ok(ApiResponse.success("Pagamento confirmado", pedido));
    }

    /**
     * Fecha a conta de um pedido e liberta a mesa.
     * Para POS_PAGO não pago: confirma o pagamento automaticamente.
     * PUT /api/pedidos/{id}/fechar
     */
    @PutMapping("/{id}/fechar")
    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE', 'ADMIN')")
    @Operation(
        summary = "Fechar conta (checkout)",
        description = "Confirma pagamento (se POS_PAGO) e liberta a mesa. "
                    + "Para PRE_PAGO, o débito já foi efectuado na criação do pedido."
    )
    public ResponseEntity<ApiResponse<PedidoResponse>> fecharConta(@PathVariable Long id) {
        PedidoResponse pedido = pedidoService.fecharConta(id);
        return ResponseEntity.ok(ApiResponse.success("Conta fechada. Mesa libertada.", pedido));
    }
}
