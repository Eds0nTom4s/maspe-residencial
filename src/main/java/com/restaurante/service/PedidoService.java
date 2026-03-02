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
import com.restaurante.notificacao.service.WebSocketNotificacaoService;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.UnidadeDeConsumoRepository;
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
    private final UnidadeDeConsumoRepository unidadeDeConsumoRepository;
    private final UnidadeDeConsumoService unidadeDeConsumoService;
    private final ProdutoService produtoService;
    private final WebSocketNotificacaoService webSocketNotificacaoService;
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
        log.info("=".repeat(80));
        log.info("🆕 CRIANDO NOVO PEDIDO");
        log.info("  ┣ Unidade de Consumo ID: {}", request.getUnidadeConsumoId());
        log.info("  ┣ Total de Itens: {}", request.getItens().size());
        log.info("  ┗ Tipo Pagamento: {}", request.getTipoPagamento());
        log.info("=".repeat(80));

        // Buscar UnidadeDeConsumo completa do repositório
        UnidadeDeConsumo unidadeConsumo = unidadeDeConsumoRepository.findById(request.getUnidadeConsumoId())
                .orElseThrow(() -> new ResourceNotFoundException("Unidade de consumo não encontrada"));

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

        log.info("💰 VALIDAÇÃO FINANCEIRA");
        log.info("  ┣ Total Calculado: {} AOA", totalPreliminar);
        log.info("  ┣ Tipo Pagamento: {}", tipoPagamento);
        log.info("  ┗ Roles Usuário: {}", roles);

        // Resolve portador: cliente identificado OU token do QR Code (anónimo)
        Long clienteId = unidadeConsumo.getCliente() != null
                ? unidadeConsumo.getCliente().getId()
                : null;
        String fundoToken = (clienteId == null) ? unidadeConsumo.getQrCode() : null;

        log.info("  ┣ Portador: {}",
                clienteId != null ? "cliente#" + clienteId : "anónimo (token:" + fundoToken + ")");

        // VALIDAÇÃO FINANCEIRA - antes de criar pedido
        pedidoFinanceiroService.validarCriacaoPedido(clienteId, fundoToken, totalPreliminar, tipoPagamento, roles);
        log.info("✅ Validação financeira APROVADA");

        // Cria o pedido
        Pedido pedido = Pedido.builder()
                .numero(gerarNumeroPedido())
                .unidadeConsumo(unidadeConsumo)
                .status(StatusPedido.CRIADO)
                .statusFinanceiro(StatusFinanceiroPedido.NAO_PAGO)  // Default
                .tipoPagamento(tipoPagamento)
                .observacoes(request.getObservacoes())
                .build();

        log.info("📄 PEDIDO CRIADO");
        log.info("  ┣ Número: {}", pedido.getNumero());
        log.info("  ┣ Status: {}", pedido.getStatus());
        log.info("  ┗ Status Financeiro: {}", pedido.getStatusFinanceiro());

        // Mapa para agrupar itens por cozinha (Cozinha -> Lista de ItemPedido)
        Map<Cozinha, List<ItemPedido>> itensPorCozinha = new HashMap<>();

        // Primeiro passo: agrupar produtos por cozinha (ainda não criar ItemPedido)
        Map<Cozinha, List<ItemPedidoRequest>> requestsPorCozinha = new HashMap<>();
        
        for (ItemPedidoRequest itemRequest : request.getItens()) {
            Produto produto = produtoService.buscarPorId(itemRequest.getProdutoId());

            // Valida se produto está ativo (disponibilidade real depende de unidades de produção)
            if (produto.getAtivo() == null || !produto.getAtivo()) {
                throw new BusinessException("Produto " + produto.getNome() + " não está ativo");
            }

            // Determinar cozinha responsável pelo item
            Cozinha cozinha = subPedidoService.determinarCozinha(produto.getCategoria(), unidadeConsumo.getUnidadeAtendimento().getId());
            
            // ✅ VALIDAÇÃO CRÍTICA: Cozinha deve estar ATIVA
            if (cozinha.getAtiva() == null || !cozinha.getAtiva()) {
                throw new BusinessException(
                    String.format("Produto '%s' não pode ser pedido: cozinha '%s' está inativa", 
                        produto.getNome(), cozinha.getNome())
                );
            }
            
            // Agrupar request por cozinha
            requestsPorCozinha.computeIfAbsent(cozinha, k -> new ArrayList<>()).add(itemRequest);
        }

        // Salvar pedido VAZIO primeiro (sem itens ainda)
        pedido = pedidoRepository.save(pedido);

        // Criar SubPedidos primeiro, DEPOIS criar ItemPedidos com subPedido já associado
        int contadorSubPedido = 1;
        for (Map.Entry<Cozinha, List<ItemPedidoRequest>> entry : requestsPorCozinha.entrySet()) {
            Cozinha cozinha = entry.getKey();
            List<ItemPedidoRequest> requests = entry.getValue();

            SubPedido subPedido = SubPedido.builder()
                    .numero(pedido.getNumero() + "-" + contadorSubPedido)
                    .pedido(pedido)
                    .cozinha(cozinha)
                    .unidadeAtendimento(unidadeConsumo.getUnidadeAtendimento())
                    .status(StatusSubPedido.CRIADO)  // ✅ Inicia em CRIADO, aguardando confirmação
                    .build();
            
            contadorSubPedido++;

            // Criar ItemPedidos JÁ COM subPedido associado
            for (ItemPedidoRequest itemRequest : requests) {
                Produto produto = produtoService.buscarPorId(itemRequest.getProdutoId());
                
                ItemPedido item = ItemPedido.builder()
                        .pedido(pedido)
                        .subPedido(subPedido)  // ✅ JÁ ASSOCIADO
                        .produto(produto)
                        .quantidade(itemRequest.getQuantidade())
                        .precoUnitario(produto.getPreco())
                        .observacoes(itemRequest.getObservacoes())
                        .build();

                item.calcularSubtotal();
                pedido.adicionarItem(item);
                subPedido.adicionarItem(item);
            }

            // ✅ ADICIONAR SubPedido à lista do Pedido (cascade vai salvar automaticamente)
            pedido.getSubPedidos().add(subPedido);
            
            log.info("SubPedido criado para cozinha {} com {} itens", cozinha.getNome(), requests.size());
        }
        
        // Recalcular total com todos os itens
        pedido.calcularTotal();
        
        // ✅ Salvar Pedido com SubPedidos (cascade salva tudo)
        pedidoRepository.save(pedido);

        log.info("📦 SUBPEDIDOS CRIADOS");
        log.info("  ┣ Total de SubPedidos: {}", requestsPorCozinha.size());
        log.info("  ┗ Total do Pedido: {} AOA", pedido.getTotal());

        // PROCESSAMENTO FINANCEIRO - depois de criar pedido e calcular total
        log.info("💳 PROCESSANDO PAGAMENTO");
        pedidoFinanceiroService.processarPagamentoPedido(pedido.getId(), clienteId, fundoToken, pedido.getTotal(), tipoPagamento);
        log.info("✅ Pagamento processado - Status: {}", pedido.getStatusFinanceiro());

        // ⚠️ RECARREGAR PEDIDO DO BANCO - com JOIN FETCH para carregar SubPedidos
        log.info("🔄 Recarregando pedido do banco para obter SubPedidos...");
        Pedido pedidoAtualizado = pedidoRepository.findByIdComSubPedidos(pedido.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado após criação"));
        log.info("✅ Pedido recarregado - {} SubPedidos encontrados", pedidoAtualizado.getSubPedidos().size());

        // CONFIRMAÇÃO AUTOMÁTICA - transita CRIADO → PENDENTE se dentro do limite
        log.info("🤖 INICIANDO CONFIRMAÇÃO AUTOMÁTICA");
        boolean confirmado = confirmarPedidoAutomaticamente(pedidoAtualizado, unidadeConsumo.getId(), tipoPagamento);

        // Atualiza status da unidade de consumo
        unidadeDeConsumoService.atualizarStatus(unidadeConsumo.getId());

        log.info("=".repeat(80));
        log.info("✅ PEDIDO CRIADO COM SUCESSO");
        log.info("  ┣ Número: {}", pedidoAtualizado.getNumero());
        log.info("  ┣ Total: {} AOA", pedidoAtualizado.getTotal());
        log.info("  ┣ SubPedidos: {}", pedidoAtualizado.getSubPedidos().size());
        log.info("  ┣ Tipo Pagamento: {}", pedidoAtualizado.getTipoPagamento());
        log.info("  ┣ Status Financeiro: {}", pedidoAtualizado.getStatusFinanceiro());
        log.info("  ┣ Confirmado Automaticamente: {}", confirmado ? "✅ SIM" : "❌ NÃO (limite atingido)");
        log.info("  ┗ Status do Pedido: {}", pedidoAtualizado.getStatus());
        log.info("=".repeat(80));

        // Enviar notificação SMS para o cliente (somente fluxo identificado)
        try {
            String telefoneCliente = (unidadeConsumo.getCliente() != null)
                ? unidadeConsumo.getCliente().getTelefone()
                : null;

            if (telefoneCliente != null) {
                notificacaoService.enviarNotificacaoPedidoCriado(
                    telefoneCliente,
                    pedidoAtualizado.getNumero(),
                    pedidoAtualizado.getTotal().doubleValue()
                );
                log.info("Notificação de pedido criado enviada para {}", telefoneCliente);
            } else {
                log.debug("Pedido anónimo ou cliente sem telefone – SMS não enviado (WebSocket cobre UI)");
            }
        } catch (Exception e) {
            // WebSocket já notificou em tempo real; SMS é complementar
            log.error("Erro ao enviar notificação SMS de pedido criado: {}", e.getMessage());
        }

        return mapToResponse(pedidoAtualizado);
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
     * Confirma o pedido - transiciona todos SubPedidos de CRIADO → PENDENTE
     * Torna o pedido visível para as cozinhas
     * 
     * IMPORTANTE: Este método deve ser chamado após criar o pedido para
     * que as cozinhas vejam os SubPedidos e possam assumir preparação
     * 
     * @param id ID do pedido
     * @return PedidoResponse atualizado
     */
    @Transactional
    public PedidoResponse confirmar(Long id) {
        log.info("Confirmando pedido ID: {}", id);
        
        Pedido pedido = buscarPorId(id);
        
        if (pedido.getStatus() != StatusPedido.CRIADO) {
            throw new BusinessException("Apenas pedidos no status CRIADO podem ser confirmados. Status atual: " + pedido.getStatus());
        }
        
        // Transiciona todos SubPedidos para PENDENTE
        for (SubPedido subPedido : pedido.getSubPedidos()) {
            if (subPedido.getStatus() == StatusSubPedido.CRIADO) {
                subPedidoService.confirmar(subPedido.getId());
                log.info("SubPedido {} confirmado (CRIADO → PENDENTE)", subPedido.getNumero());
            }
        }
        
        // Recalcula status do pedido (deve ficar EM_ANDAMENTO)
        recalcularStatusPedido(id);
        
        pedido = buscarPorId(id); // Recarrega após recálculo
        
        log.info("Pedido {} confirmado com sucesso. Status: {}", pedido.getNumero(), pedido.getStatus());
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
     * Fecha a conta de um pedido:
     * 1. Para POS_PAGO não pago: confirma o pagamento automaticamente.
     * 2. Liberta a unidade de consumo (mesa/quarto) chamando fechar().
     *
     * <p>Para PRE_PAGO, o débito já foi efectuado na criação do pedido —
     * esta operação apenas liberta a mesa.
     *
     * @param id ID do pedido a fechar
     * @return PedidoResponse actualizado
     */
    @Transactional
    public PedidoResponse fecharConta(Long id) {
        log.info("Fechando conta do pedido ID: {}", id);

        Pedido pedido = buscarPorId(id);

        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new BusinessException(
                    "Pedido está cancelado. Não é possível fechar a conta.");
        }

        // POS_PAGO e ainda não pago: confirma agora
        if (pedido.getTipoPagamento().isPosPago() && !pedido.isPago()) {
            log.info("Pedido {} é POS_PAGO e não pago — confirmando pagamento", pedido.getNumero());
            pedidoFinanceiroService.confirmarPagamentoPosPago(id);
        }

        // Liberta a mesa / unidade de consumo
        if (pedido.getUnidadeConsumo() != null) {
            Long unidadeId = pedido.getUnidadeConsumo().getId();
            try {
                unidadeDeConsumoService.fechar(unidadeId);
                log.info("Unidade de consumo {} libertada", unidadeId);
            } catch (Exception e) {
                // Não falha o checkout se a mesa já estiver no estado correcto
                log.warn("Aviso ao fechar unidade {}: {}", unidadeId, e.getMessage());
            }
        }

        Pedido pedidoAtualizado = buscarPorId(id);
        log.info("Conta fechada — pedido {}, status financeiro: {}",
                pedidoAtualizado.getNumero(), pedidoAtualizado.getStatusFinanceiro());
        return mapToResponse(pedidoAtualizado);
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
     * Confirma pedido automaticamente se dentro do limite de risco
     * 
     * REGRA DE OURO: "O limite substitui o humano enquanto houver margem"
     * 
     * - Valida limite de pós-pago (se aplicável)
     * - Se DENTRO do limite: transita SubPedidos CRIADO → PENDENTE
     * - Se FORA do limite: mantém SubPedidos em CRIADO (bloqueado)
     * - Notifica em tempo real via WebSocket
     * 
     * @param pedido Pedido a ser confirmado
     * @param unidadeConsumoId ID da unidade de consumo
     * @param tipoPagamento Tipo de pagamento do pedido
     * @return true se confirmado, false se bloqueado por limite
     */
    @Transactional
    private boolean confirmarPedidoAutomaticamente(Pedido pedido, Long unidadeConsumoId, TipoPagamentoPedido tipoPagamento) {
        log.info("━".repeat(80));
        log.info("🤖 CONFIRMAÇÃO AUTOMÁTICA - Pedido {}", pedido.getNumero());
        log.info("  ┣ Tipo Pagamento: {}", tipoPagamento);
        log.info("  ┣ Total: {} AOA", pedido.getTotal());
        log.info("  ┗ SubPedidos: {}", pedido.getSubPedidos().size());

        // Obter roles do usuário autenticado
        Set<String> roles = obterRolesUsuarioAutenticado();
        log.info("🔑 Roles do usuário: {}", roles);

        // Valida se pedido pode ser confirmado (dentro do limite)
        log.info("⌛ Validando limite de pós-pago...");
        boolean podeConfirmar = pedidoFinanceiroService.validarEConfirmarSePermitido(
            unidadeConsumoId, 
            pedido.getTotal(), 
            tipoPagamento, 
            roles
        );

        if (!podeConfirmar) {
            // BLOQUEADO: Limite atingido - mantém em CRIADO
            log.warn("━".repeat(80));
            log.warn("❌ PEDIDO BLOQUEADO - Limite de pós-pago atingido");
            log.warn("  ┣ Pedido: {}", pedido.getNumero());
            log.warn("  ┣ Total: {} AOA", pedido.getTotal());
            log.warn("  ┣ Status mantido: CRIADO");
            log.warn("  ┗ Ação: Notificando gerente sobre limite atingido");
            log.warn("━".repeat(80));
            webSocketNotificacaoService.notificarPedidoBloqueadoPorLimite(pedido);
            return false;
        }

        // LIBERADO: Dentro do limite - confirma automaticamente
        log.info("━".repeat(80));
        log.info("✅ PEDIDO LIBERADO - Dentro do limite");
        log.info("  ┗ Iniciando transição CRIADO → PENDENTE para {} SubPedidos", pedido.getSubPedidos().size());

        // ⚠️ CRIAR CÓPIA DA LISTA para evitar ConcurrentModificationException
        // (a lista é gerenciada pelo Hibernate e pode ser modificada durante iteração)
        List<SubPedido> subPedidosCopia = new ArrayList<>(pedido.getSubPedidos());
        
        // Transita todos os SubPedidos de CRIADO → PENDENTE
        int contador = 0;
        for (SubPedido subPedido : subPedidosCopia) {
            if (subPedido.getStatus() == StatusSubPedido.CRIADO) {
                contador++;
                log.info("  🔄 SubPedido {}/{}: {}", contador, subPedidosCopia.size(), subPedido.getNumero());
                log.info("    ┣ Status Anterior: CRIADO");
                log.info("    ┣ Cozinha: {}", subPedido.getCozinha().getNome());
                log.info("    ┣ Itens: {}", subPedido.getItens().size());
                
                subPedidoService.confirmar(subPedido.getId());
                
                log.info("    ┗ Status Atual: PENDENTE ✅");
            }
        }

        // Notifica em tempo real: cozinha, bar, painel gerente
        log.info("━".repeat(80));
        log.info("📡 NOTIFICANDO EM TEMPO REAL");
        log.info("  ┣ Cozinhas: {} unidades", pedido.getSubPedidos().stream().map(sp -> sp.getCozinha().getId()).distinct().count());
        log.info("  ┗ Painel Gerente: broadcast global");
        webSocketNotificacaoService.notificarPedidoLiberadoAutomaticamente(pedido);
        log.info("✅ Notificações enviadas com sucesso");
        log.info("━".repeat(80));

        return true;
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
