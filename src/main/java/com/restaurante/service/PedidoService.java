package com.restaurante.service;

import com.restaurante.dto.request.CriarPedidoRequest;
import com.restaurante.dto.request.ItemPedidoRequest;
import com.restaurante.dto.response.ItemPedidoResponse;
import com.restaurante.dto.response.PedidoResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.entity.ItemPedido;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.entity.UnidadeDeConsumo;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service para operações de negócio com Pedido
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final UnidadeDeConsumoService unidadeDeConsumoService;
    private final ProdutoService produtoService;
    private final SubPedidoService subPedidoService;
    private final EventLogService eventLogService;
    // TODO: Injetar NotificacaoService para WebSocket

    /**
     * Cria um novo pedido para uma unidade de consumo
     * Automaticamente cria SubPedidos agrupando itens por cozinha responsável
     */
    @Transactional
    public PedidoResponse criar(CriarPedidoRequest request) {
        log.info("Criando novo pedido para unidade de consumo ID: {}", request.getUnidadeConsumoId());

        // Buscar UnidadeDeConsumo por ID via service
        var unidadeConsumoResponse = unidadeDeConsumoService.buscarPorId(request.getUnidadeConsumoId());
        
        // Para simplificar, buscar entity novamente (TODO: refatorar service para retornar entity)
        UnidadeDeConsumo unidadeConsumo = new UnidadeDeConsumo();
        unidadeConsumo.setId(unidadeConsumoResponse.getId());

        // Cria o pedido
        Pedido pedido = Pedido.builder()
                .numero(gerarNumeroPedido())
                .unidadeConsumo(unidadeConsumo)
                .status(StatusPedido.PENDENTE)
                .observacoes(request.getObservacoes())
                .build();

        // Mapa para agrupar itens por cozinha (Cozinha -> Lista de ItemPedido)
        Map<Cozinha, List<ItemPedido>> itensPorCozinha = new HashMap<>();

        // Adiciona os itens ao pedido e agrupa por cozinha
        for (ItemPedidoRequest itemRequest : request.getItens()) {
            Produto produto = produtoService.buscarPorId(itemRequest.getProdutoId());

            if (!produto.getDisponivel()) {
                throw new BusinessException("Produto " + produto.getNome() + " não está disponível");
            }

            // Determinar cozinha responsável pelo item
            Cozinha cozinha = subPedidoService.determinarCozinha(produto.getCategoria(), unidadeConsumo.getUnidadeAtendimento().getId());

            ItemPedido item = ItemPedido.builder()
                    .pedido(pedido)
                    .produto(produto)
                    .quantidade(itemRequest.getQuantidade())
                    .precoUnitario(produto.getPreco())
                    .observacoes(itemRequest.getObservacoes())
                    .build();

            item.calcularSubtotal();
            pedido.adicionarItem(item);

            // Agrupar item por cozinha
            itensPorCozinha.computeIfAbsent(cozinha, k -> new ArrayList<>()).add(item);
        }

        pedido.calcularTotal();
        pedido = pedidoRepository.save(pedido);

        // Criar SubPedidos automaticamente - um para cada cozinha
        for (Map.Entry<Cozinha, List<ItemPedido>> entry : itensPorCozinha.entrySet()) {
            Cozinha cozinha = entry.getKey();
            List<ItemPedido> itens = entry.getValue();

            SubPedido subPedido = SubPedido.builder()
                    .pedido(pedido)
                    .cozinha(cozinha)
                    .unidadeAtendimento(unidadeConsumo.getUnidadeAtendimento())
                    .status(StatusSubPedido.PENDENTE)
                    .build();

            // Adicionar itens ao subpedido
            for (ItemPedido item : itens) {
                item.setSubPedido(subPedido);
                subPedido.adicionarItem(item);
            }

            // Salvar subpedido
            subPedidoService.salvar(subPedido);
            
            log.info("SubPedido criado para cozinha {} com {} itens", cozinha.getNome(), itens.size());
        }

        // Atualiza status da unidade de consumo
        unidadeDeConsumoService.atualizarStatus(unidadeConsumoResponse.getId());

        log.info("Pedido criado com sucesso - Número: {}, Total: {}, SubPedidos: {}", 
                pedido.getNumero(), pedido.getTotal(), itensPorCozinha.size());

        // TODO: Enviar notificação via WebSocket para atendentes
        // notificacaoService.notificarNovoPedido(pedido);

        return mapToResponse(pedido);
    }

    /**
     * Busca pedido por ID e retorna Response
     */
    @Transactional(readOnly = true)
    public PedidoResponse buscarPorIdComResponse(Long id) {
        Pedido pedido = buscarPorId(id);
        return mapToResponse(pedido);
    }

    /**
     * Busca pedido por ID
     */
    @Transactional(readOnly = true)
    public Pedido buscarPorId(Long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", "id", id));
    }

    /**
     * Busca pedido por número
     */
    @Transactional(readOnly = true)
    public PedidoResponse buscarPorNumero(String numero) {
        log.info("Buscando pedido por número: {}", numero);
        Pedido pedido = pedidoRepository.findByNumero(numero)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", "numero", numero));
        
        return mapToResponse(pedido);
    }

    /**
     * Lista pedidos de uma mesa
     */
    @Transactional(readOnly = true)
    public List<PedidoResponse> listarPorMesa(Long mesaId) {
        log.info("Listando pedidos da mesa ID: {}", mesaId);
        return pedidoRepository.findByMesaIdOrderByCreatedAtDesc(mesaId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lista pedidos por status
     */
    @Transactional(readOnly = true)
    public List<PedidoResponse> listarPorStatus(StatusPedido status) {
        log.info("Listando pedidos com status: {}", status);
        return pedidoRepository.findByStatusOrderByCreatedAtAsc(status)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lista pedidos pendentes e recebidos (para painel do atendente)
     */
    @Transactional(readOnly = true)
    public List<PedidoResponse> listarPedidosAtivos() {
        log.info("Listando pedidos ativos");
        List<StatusPedido> statusAtivos = List.of(StatusPedido.PENDENTE, StatusPedido.RECEBIDO, StatusPedido.EM_PREPARO);
        return pedidoRepository.findByStatusInOrderByCreatedAtAsc(statusAtivos)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Atualiza o status de um pedido
     */
    @Transactional
    public PedidoResponse atualizarStatus(Long id, StatusPedido novoStatus) {
        log.info("Atualizando status do pedido ID: {} para {}", id, novoStatus);
        
        Pedido pedido = buscarPorId(id);
        StatusPedido statusAnterior = pedido.getStatus();
        pedido.setStatus(novoStatus);
        pedido = pedidoRepository.save(pedido);

        // Registra evento de auditoria
        eventLogService.registrarEventoPedido(pedido, statusAnterior, novoStatus, null, null);

        // Atualiza status da mesa
        unidadeDeConsumoService.atualizarStatus(pedido.getUnidadeConsumo().getId());

        // TODO: Enviar notificação via WebSocket
        // notificacaoService.notificarAtualizacaoPedido(pedido);

        return mapToResponse(pedido);
    }

    /**
     * Avança o status do pedido para o próximo estado
     */
    @Transactional
    public PedidoResponse avancarStatus(Long id) {
        log.info("Avançando status do pedido ID: {}", id);
        
        Pedido pedido = buscarPorId(id);
        StatusPedido statusAnterior = pedido.getStatus();
        pedido.avancarStatus();
        pedido = pedidoRepository.save(pedido);

        // Registra evento de auditoria
        eventLogService.registrarEventoPedido(pedido, statusAnterior, pedido.getStatus(), null, "Avanço automático");

        // Atualiza status da mesa
        unidadeDeConsumoService.atualizarStatus(pedido.getUnidadeConsumo().getId());

        log.info("Status do pedido {} atualizado para {}", pedido.getNumero(), pedido.getStatus());
        
        return mapToResponse(pedido);
    }

    /**
     * Cancela um pedido
     */
    @Transactional
    public PedidoResponse cancelar(Long id) {
        log.info("Cancelando pedido ID: {}", id);
        
        Pedido pedido = buscarPorId(id);

        if (!pedido.podeCancelar()) {
            throw new BusinessException("Pedido não pode ser cancelado. Status atual: " + pedido.getStatus());
        }

        StatusPedido statusAnterior = pedido.getStatus();
        pedido.setStatus(StatusPedido.CANCELADO);
        pedido = pedidoRepository.save(pedido);

        // Registra evento de auditoria
        eventLogService.registrarEventoPedido(pedido, statusAnterior, StatusPedido.CANCELADO, null, "Pedido cancelado");

        log.info("Pedido {} cancelado com sucesso", pedido.getNumero());
        return mapToResponse(pedido);
    }

    /**
     * Gera número único do pedido no formato PED-YYYYMMDD-XXX
     */
    private String gerarNumeroPedido() {
        String dataAtual = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = pedidoRepository.count() + 1;
        return String.format("PED-%s-%03d", dataAtual, count);
    }

    /**
     * Mapeia Pedido para PedidoResponse
     */
    private PedidoResponse mapToResponse(Pedido pedido) {
        return PedidoResponse.builder()
                .id(pedido.getId())
                .numero(pedido.getNumero())
                .status(pedido.getStatus())
                .observacoes(pedido.getObservacoes())
                .total(pedido.getTotal())
                .unidadeConsumoId(pedido.getUnidadeConsumo().getId())
                .referenciaUnidadeConsumo(pedido.getUnidadeConsumo().getReferencia())
                .itens(pedido.getItens().stream()
                        .map(this::mapItemToResponse)
                        .collect(Collectors.toList()))
                .createdAt(pedido.getCreatedAt())
                .updatedAt(pedido.getUpdatedAt())
                .build();
    }

    private ItemPedidoResponse mapItemToResponse(ItemPedido item) {
        return ItemPedidoResponse.builder()
                .id(item.getId())
                .produtoId(item.getProduto().getId())
                .produtoNome(item.getProduto().getNome())
                .produtoCodigo(item.getProduto().getCodigo())
                .quantidade(item.getQuantidade())
                .precoUnitario(item.getPrecoUnitario())
                .subtotal(item.getSubtotal())
                .observacoes(item.getObservacoes())
                .build();
    }
}
