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
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TipoPagamentoPedido;
import com.restaurante.notificacao.service.NotificacaoService;
import com.restaurante.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final PedidoFinanceiroService pedidoFinanceiroService;
    private final NotificacaoService notificacaoService;

    /**
     * Cria um novo pedido para uma unidade de consumo
     * Automaticamente cria SubPedidos agrupando itens por cozinha responsável
     * 
     * VALIDAÇÃO FINANCEIRA:
     * - Extrai tipoPagamento do request (default: PRE_PAGO)
     * - Valida permissões (PRE_PAGO vs POS_PAGO)
     * - Valida saldo suficiente (se PRE_PAGO)
     * - Processa pagamento automático (se PRE_PAGO)
     */
    @Transactional
    public PedidoResponse criar(CriarPedidoRequest request) {
        log.info("Criando novo pedido para unidade de consumo ID: {}", request.getUnidadeConsumoId());

        // Buscar UnidadeDeConsumo por ID via service
        var unidadeConsumoResponse = unidadeDeConsumoService.buscarPorId(request.getUnidadeConsumoId());
        
        // Para simplificar, buscar entity novamente (TODO: refatorar service para retornar entity)
        UnidadeDeConsumo unidadeConsumo = new UnidadeDeConsumo();
        unidadeConsumo.setId(unidadeConsumoResponse.getId());

        // Determina tipo de pagamento (default: PRE_PAGO)
        TipoPagamentoPedido tipoPagamento = request.getTipoPagamento() != null 
            ? request.getTipoPagamento() 
            : TipoPagamentoPedido.PRE_PAGO;

        // Obtem roles do usuário autenticado
        Set<String> roles = obterRolesUsuarioAutenticado();

        // Calcula total ANTES de criar o pedido (para validação financeira)
        var totalPreliminar = request.getItens().stream()
            .map(item -> {
                Produto produto = produtoService.buscarPorId(item.getProdutoId());
                return produto.getPreco().multiply(java.math.BigDecimal.valueOf(item.getQuantidade()));
            })
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        // VALIDAÇÃO FINANCEIRA - antes de criar pedido
        Long clienteId = obterClienteId(unidadeConsumoResponse);
        pedidoFinanceiroService.validarCriacaoPedido(clienteId, totalPreliminar, tipoPagamento, roles);

        // Cria o pedido
        Pedido pedido = Pedido.builder()
                .numero(gerarNumeroPedido())
                .unidadeConsumo(unidadeConsumo)
                .status(StatusPedido.CRIADO)
                .statusFinanceiro(StatusFinanceiroPedido.NAO_PAGO)  // Default
                .tipoPagamento(tipoPagamento)
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

        // PROCESSAMENTO FINANCEIRO - depois de criar pedido
        pedidoFinanceiroService.processarPagamentoPedido(pedido.getId(), clienteId, pedido.getTotal(), tipoPagamento);

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

        log.info("Pedido criado com sucesso - Número: {}, Total: {}, SubPedidos: {}, Tipo Pagamento: {}, Status Financeiro: {}", 
                pedido.getNumero(), pedido.getTotal(), itensPorCozinha.size(), pedido.getTipoPagamento(), pedido.getStatusFinanceiro());

        // Enviar notificação SMS para o cliente
        try {
            String telefoneCliente = unidadeConsumo.getCliente() != null 
                ? unidadeConsumo.getCliente().getTelefone() 
                : null;
            
            if (telefoneCliente != null) {
                notificacaoService.enviarNotificacaoPedidoCriado(
                    telefoneCliente, 
                    pedido.getNumero(), 
                    pedido.getTotal().doubleValue()
                );
                log.info("Notificação de pedido criado enviada para {}", telefoneCliente);
            } else {
                log.warn("Cliente sem telefone cadastrado - notificação não enviada");
            }
        } catch (Exception e) {
            log.error("Erro ao enviar notificação de pedido criado: {}", e.getMessage());
            // Não falhar a transação se notificação falhar
        }

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
        List<StatusPedido> statusAtivos = List.of(StatusPedido.CRIADO, StatusPedido.EM_ANDAMENTO);
        return pedidoRepository.findByStatusInOrderByCreatedAtAsc(statusAtivos)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * ⭐ FONTE ÚNICA DE VERDADE - CÁLCULO DE STATUS DO PEDIDO ⭐
     * 
     * REGRAS DE NEGÓCIO CRÍTICAS:
     * - StatusPedido é DERIVADO dos SubPedidos (NUNCA setado manualmente)
     * - Este é o ÚNICO método que pode alterar pedido.status
     * - Deve ser chamado após QUALQUER mudança em SubPedido
     * 
     * MAPEAMENTO:
     * - TODOS SubPedidos = ENTREGUE → FINALIZADO
     * - QUALQUER SubPedido = EM_PREPARACAO, PRONTO ou PENDENTE → EM_ANDAMENTO
     * - TODOS SubPedidos = CANCELADO → CANCELADO
     * - Caso contrário (inicial) → CRIADO
     * 
     * AUDITORIA:
     * - Registra EventLog se status mudou
     * 
     * @param pedidoId ID do pedido a recalcular
     * @return StatusPedido calculado
     */
    @Transactional
    public StatusPedido recalcularStatusPedido(Long pedidoId) {
        log.debug("Recalculando status do pedido ID: {}", pedidoId);
        
        Pedido pedido = buscarPorId(pedidoId);
        StatusPedido statusAnterior = pedido.getStatus();
        
        List<SubPedido> subPedidos = pedido.getSubPedidos();
        
        if (subPedidos == null || subPedidos.isEmpty()) {
            log.warn("Pedido {} sem SubPedidos. Mantendo status: {}", pedido.getNumero(), statusAnterior);
            return statusAnterior;
        }
        
        StatusPedido novoStatus = calcularStatusBaseadoEmSubPedidos(subPedidos);
        
        // Só atualiza se mudou
        if (novoStatus != statusAnterior) {
            pedido.setStatus(novoStatus);
            pedidoRepository.save(pedido);
            
            // Auditoria
            eventLogService.registrarEventoPedido(pedido, statusAnterior, novoStatus, null, 
                "Status recalculado automaticamente baseado em SubPedidos");
            
            log.info("Pedido {} status alterado: {} → {}", pedido.getNumero(), statusAnterior, novoStatus);
        }
        
        return novoStatus;
    }
    
    /**
     * Calcula status baseado em SubPedidos (lógica pura)
     */
    private StatusPedido calcularStatusBaseadoEmSubPedidos(List<SubPedido> subPedidos) {
        boolean todosEntregues = subPedidos.stream()
            .allMatch(sub -> sub.getStatus() == StatusSubPedido.ENTREGUE);
        
        if (todosEntregues) {
            return StatusPedido.FINALIZADO;
        }
        
        boolean todosCancelados = subPedidos.stream()
            .allMatch(sub -> sub.getStatus() == StatusSubPedido.CANCELADO);
        
        if (todosCancelados) {
            return StatusPedido.CANCELADO;
        }
        
        boolean algumEmAndamento = subPedidos.stream()
            .anyMatch(sub -> 
                sub.getStatus() == StatusSubPedido.PENDENTE ||
                sub.getStatus() == StatusSubPedido.EM_PREPARACAO ||
                sub.getStatus() == StatusSubPedido.PRONTO
            );
        
        if (algumEmAndamento) {
            return StatusPedido.EM_ANDAMENTO;
        }
        
        // Estado inicial (todos CRIADO)
        return StatusPedido.CRIADO;
    }

    /**
     * Cancela um pedido
     * 
     * IMPORTANTE: Cancela todos SubPedidos primeiro, depois recalcula status
     * 
     * ESTORNO AUTOMÁTICO:
     * - Se pedido está PAGO, estorna automaticamente
     * - Se PRE_PAGO, devolve para Fundo de Consumo
     * - Se POS_PAGO, apenas marca como ESTORNADO
     * 
     * @param id ID do pedido
     * @param motivo Motivo obrigatório do cancelamento
     */
    @Transactional
    public PedidoResponse cancelar(Long id, String motivo) {
        log.info("Cancelando pedido ID: {} com motivo: {}", id, motivo);
        
        // Validação de motivo obrigatório
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new BusinessException("Motivo é obrigatório para cancelar pedido");
        }
        
        Pedido pedido = buscarPorId(id);

        if (!pedido.podeCancelar()) {
            throw new BusinessException("Pedido não pode ser cancelado. Status atual: " + pedido.getStatus());
        }

        // Cancelar todos SubPedidos primeiro
        for (SubPedido subPedido : pedido.getSubPedidos()) {
            if (!subPedido.isTerminal()) {
                subPedidoService.cancelar(subPedido.getId(), motivo);
            }
        }
        
        // ESTORNO FINANCEIRO AUTOMÁTICO
        if (pedido.isPago()) {
            log.info("Pedido {} estava PAGO. Iniciando estorno automático.", pedido.getNumero());
            pedidoFinanceiroService.estornarPedido(id, motivo);
        }
        
        // Recalcular status (deve ficar CANCELADO após cancelar SubPedidos)
        recalcularStatusPedido(id);
        
        pedido = buscarPorId(id); // Recarregar após recálculo

        log.info("Pedido {} cancelado com sucesso. Status: {}, Status financeiro: {}", 
            pedido.getNumero(), pedido.getStatus(), pedido.getStatusFinanceiro());
        return mapToResponse(pedido);
    }

    /**
     * Confirma pagamento de pedido pós-pago (GERENTE/ADMIN)
     */
    @Transactional
    public PedidoResponse confirmarPagamento(Long id) {
        log.info("Confirmando pagamento do pedido ID: {}", id);
        
        pedidoFinanceiroService.confirmarPagamentoPosPago(id);
        
        Pedido pedido = buscarPorId(id);
        
        log.info("Pagamento confirmado para pedido {}", pedido.getNumero());
        return mapToResponse(pedido);
    }

    /**
     * Obtém roles do usuário autenticado
     */
    private Set<String> obterRolesUsuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return Set.of();
        }

        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(role -> role.replace("ROLE_", ""))  // Remove prefixo "ROLE_"
            .collect(Collectors.toSet());
    }

    /**
     * Obtém clienteId da unidade de consumo
     * TODO: Implementar vínculo real com Cliente quando estiver disponível
     */
    private Long obterClienteId(com.restaurante.dto.response.UnidadeConsumoResponse unidadeConsumo) {
        // PLACEHOLDER: retorna 1L temporariamente
        // Em produção, UnidadeDeConsumo deve ter clienteId ou vincular via sessão/autenticação
        return 1L;
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
