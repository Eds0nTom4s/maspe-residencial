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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import java.util.List;

/**
 * Controller REST para operações com Pedido
 */
@RestController
@RequestMapping("/pedidos")
@RequiredArgsConstructor
@Tag(name = "Pedidos", description = "Endpoints para gestão de pedidos")
@Slf4j
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
     * Cria um novo pedido a partir do Cliente (QR Code)
     * POST /api/pedidos/cliente
     */
    @PostMapping("/cliente")
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(summary = "Cliente criar pedido", description = "Cria um novo pedido para a sessão ativa do cliente")
    public ResponseEntity<ApiResponse<PedidoResponse>> criarPedidoCliente(@Valid @RequestBody CriarPedidoRequest request) {
        log.info("🛒 RECEBENDO PEDIDO DO CLIENTE");
        log.info("  ┣ Sessão ID (Request): {}", request.getSessaoConsumoId());
        log.info("  ┣ Itens: {}", request.getItens() != null ? request.getItens().size() : "NULL");
        log.info("  ┣ Tipo Pagamento: {}", request.getTipoPagamento());
        log.info("  ┗ Fundo Externo: {}", request.getQrCodeFundo() != null ? "SIM" : "NÃO");

        String telefone = getUsuarioLogado();
        PedidoResponse pedido = pedidoService.criarPedidoCliente(request, telefone);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Pedido enviado para a cozinha", pedido));
    }

    /**
     * Lista pedidos da sessão do cliente conectado
     * GET /api/pedidos/cliente
     */
    @GetMapping("/cliente")
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(summary = "Meus pedidos", description = "Lista todos os pedidos da sessão ativa do cliente (QR ou Login)")
    public ResponseEntity<ApiResponse<List<PedidoResponse>>> listarMeusPedidos() {
        String telefone = getUsuarioLogado();
        List<PedidoResponse> pedidos = pedidoService.listarPedidosPorCliente(telefone);
        return ResponseEntity.ok(ApiResponse.success("Seus pedidos efetuados", pedidos));
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
     * Lista todos os pedidos de hoje (dashboard admin) com paginação
     */
    @GetMapping("/hoje")
    @PreAuthorize("hasAnyRole('ADMIN', 'ATENDENTE')")
    @Operation(summary = "Pedidos hoje", description = "Lista todos os pedidos do dia atual com paginação")
    public ApiResponse<Page<PedidoResponse>> pedidosHoje(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success("Pedidos de hoje listados com sucesso", pedidoService.listarPedidosHoje(pageable));
    }

    /**
     * Lista pedidos por status com paginação
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ATENDENTE')")
    @Operation(summary = "Listar pedidos por status", description = "Lista pedidos filtrados por status com paginação")
    public ApiResponse<Page<PedidoResponse>> listarPorStatus(
            @PathVariable StatusPedido status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.success("Pedidos filtrados por status", pedidoService.listarPorStatus(status, pageable));
    }

    /**
     * Lista pedidos ativos (CRIADO, EM_ANDAMENTO) com paginação
     */
    @GetMapping("/ativos")
    @PreAuthorize("hasAnyRole('ADMIN', 'ATENDENTE')")
    @Operation(summary = "Listar pedidos ativos", description = "Lista pedidos pendentes, recebidos e em preparo com paginação")
    public ApiResponse<Page<PedidoResponse>> listarAtivos(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.success("Pedidos ativos listados com sucesso", pedidoService.listarPedidosAtivos(pageable));
    }

    /**
     * Lista pedidos por sessão de consumo com paginação
     */
    @GetMapping("/sessao/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ATENDENTE')")
    @Operation(summary = "Listar pedidos por sessão", description = "Lista todos os pedidos de uma sessão de consumo com paginação")
    public ApiResponse<Page<PedidoResponse>> listarPorSessaoConsumo(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.success("Pedidos da sessão listados com sucesso", pedidoService.listarPorSessaoConsumo(id, pageable));
    }

    /**
     * Lista apenas pedidos ativos de uma sessão com paginação
     */
    @GetMapping("/sessao/{id}/ativos")
    @PreAuthorize("hasAnyRole('ADMIN', 'ATENDENTE')")
    @Operation(summary = "Pedidos activos da sessão", description = "Lista pedidos CRIADO ou EM_ANDAMENTO de uma sessão com paginação")
    public ApiResponse<Page<PedidoResponse>> listarAtivosPorSessaoConsumo(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.success("Pedidos ativos da sessão listados com sucesso", pedidoService.listarAtivosPorSessaoConsumo(id, pageable));
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

    /**
     * Paga o pedido usando um fundo de consumo (via QR Code).
     * O QR Code pode ser de qualquer fundo ativo (próprio ou de terceiros).
     * POST /api/pedidos/{id}/pagar-com-fundo
     */
    @PostMapping("/{id}/pagar-com-fundo")
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(
        summary = "Pagar com fundo de consumo",
        description = "Debita o valor do pedido de um QR Code de fundo de consumo escaneado."
    )
    public ResponseEntity<ApiResponse<PedidoResponse>> pagarComFundo(
            @PathVariable Long id,
            @RequestParam String qrCodeFundo) {
        String telefonePagador = getUsuarioLogado();
        PedidoResponse pedido = pedidoService.pagarComFundoExterno(id, qrCodeFundo, telefonePagador);
        return ResponseEntity.ok(ApiResponse.success("Pagamento efetuado com sucesso via fundo de consumo", pedido));
    }

    /**
     * Lista pedidos com filtros avançados e paginação. Todos os parâmetros são opcionais.
     * GET /api/pedidos?status=FINALIZADO&sessaoId=1&page=0&size=20
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ATENDENTE', 'GERENTE', 'ADMIN')")
    @Operation(summary = "[Admin] Listar pedidos com filtros",
               description = "Filtros opcionais: status, sessaoId, dataInicio, dataFim. Suporta paginação.")
    public ApiResponse<Page<PedidoResponse>> listarComFiltros(
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) Long sessaoId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime dataInicio,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime dataFim,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success("Pedidos listados", pedidoService.listarComFiltros(status, sessaoId, dataInicio, dataFim, pageable));
    }

    private String getUsuarioLogado() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
