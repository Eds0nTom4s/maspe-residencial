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
     * Cancela um pedido
     * PUT /api/pedidos/{id}/cancelar
     */
    @PutMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar pedido", description = "Cancela um pedido (apenas se PENDENTE ou RECEBIDO)")
    public ResponseEntity<ApiResponse<PedidoResponse>> cancelar(@PathVariable Long id) {
        PedidoResponse pedido = pedidoService.cancelar(id);
        return ResponseEntity.ok(ApiResponse.success("Pedido cancelado com sucesso", pedido));
    }
}
