package com.restaurante.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.CategoriaProduto;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TipoCozinha;
import com.restaurante.repository.CozinhaRepository;
import com.restaurante.repository.SubPedidoRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.service.validator.TransicaoEstadoValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service para operações de negócio com SubPedido
 * 
 * MOTOR OPERACIONAL - BACKEND FIRST
 * 
 * Responsabilidades:
 * - Gerenciar ciclo de vida completo do SubPedido
 * - Aplicar máquina de estados rigorosa
 * - Validar permissões por role
 * - Garantir idempotência
 * - Registrar eventos de auditoria
 */
@Service
@Slf4j
public class SubPedidoService {

    private final SubPedidoRepository subPedidoRepository;
    private final CozinhaRepository cozinhaRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final EventLogService eventLogService;
    private final TransicaoEstadoValidator transicaoValidator;
    private final PedidoService pedidoService; // Injeção lazy para evitar circular dependency

    public SubPedidoService(
        SubPedidoRepository subPedidoRepository,
        CozinhaRepository cozinhaRepository,
        UnidadeAtendimentoRepository unidadeAtendimentoRepository,
        EventLogService eventLogService,
        TransicaoEstadoValidator transicaoValidator,
        @Lazy PedidoService pedidoService // LAZY: evita dependência circular
    ) {
        this.subPedidoRepository = subPedidoRepository;
        this.cozinhaRepository = cozinhaRepository;
        this.unidadeAtendimentoRepository = unidadeAtendimentoRepository;
        this.eventLogService = eventLogService;
        this.transicaoValidator = transicaoValidator;
        this.pedidoService = pedidoService;
    }

    /**
     * LÓGICA DE ROTEAMENTO AUTOMÁTICO
     * Determina qual cozinha deve preparar baseado na categoria do produto
     */
    public TipoCozinha determinarTipoCozinha(CategoriaProduto categoria) {
        return switch (categoria) {
            case ENTRADA, PRATO_PRINCIPAL, ACOMPANHAMENTO, LANCHE -> TipoCozinha.CENTRAL;
            case PIZZA -> TipoCozinha.PIZZARIA;
            case SOBREMESA -> TipoCozinha.CONFEITARIA;
            case BEBIDA_ALCOOLICA, BEBIDA_NAO_ALCOOLICA -> TipoCozinha.BAR_PREP;
            case OUTROS -> TipoCozinha.CENTRAL; // Fallback para cozinha central
        };
    }

    /**
     * Salva um SubPedido (usado internamente por PedidoService)
     */
    @Transactional
    public SubPedido salvar(SubPedido subPedido) {
        return subPedidoRepository.save(subPedido);
    }

    /**
     * Determina cozinha responsável por categoria de produto
     * Busca cozinha ativa do tipo adequado vinculada à unidade de atendimento
     */
    @Transactional(readOnly = true)
    public Cozinha determinarCozinha(CategoriaProduto categoria, Long unidadeAtendimentoId) {
        TipoCozinha tipoCozinha = determinarTipoCozinha(categoria);
        
        log.debug("Determinando cozinha para categoria {} -> tipo {}", categoria, tipoCozinha);
        
        // Busca cozinha ativa do tipo correto vinculada à unidade
        List<Cozinha> cozinhas = cozinhaRepository.findByUnidadeAtendimentoIdAndTipoAndAtiva(
            unidadeAtendimentoId, tipoCozinha, true
        );
        
        if (cozinhas.isEmpty()) {
            // Fallback: busca qualquer cozinha ativa desse tipo
            cozinhas = cozinhaRepository.findByAtivaAndTipo(true, tipoCozinha);
            
            if (cozinhas.isEmpty()) {
                throw new BusinessException(
                    "Nenhuma cozinha ativa encontrada para preparar produtos do tipo: " + categoria
                );
            }
            
            log.warn("Usando cozinha fallback não vinculada à unidade de atendimento");
        }
        
        // Seleciona cozinha com menor carga (menor número de SubPedidos ativos)
        return cozinhas.stream()
            .min((c1, c2) -> {
                long carga1 = cozinhaRepository.contarSubPedidosAtivosPorCozinha(c1.getId());
                long carga2 = cozinhaRepository.contarSubPedidosAtivosPorCozinha(c2.getId());
                return Long.compare(carga1, carga2);
            })
            .orElse(cozinhas.get(0));
    }

    /**
     * Cria SubPedido (sempre CRIADO inicialmente)
     */
    @Transactional
    public SubPedido criar(Pedido pedido, Cozinha cozinha, UnidadeAtendimento unidadeAtendimento, 
                          List<ItemPedido> itens, String observacoes) {
        log.info("Criando SubPedido para Pedido {} - Cozinha: {}", pedido.getNumero(), cozinha.getNome());

        SubPedido subPedido = SubPedido.builder()
                .pedido(pedido)
                .cozinha(cozinha)
                .unidadeAtendimento(unidadeAtendimento)
                .status(StatusSubPedido.CRIADO)
                .observacoes(observacoes)
                .build();

        SubPedido subPedidoSalvo = subPedidoRepository.save(subPedido);
        
        // Vincula itens ao SubPedido
        itens.forEach(item -> item.setSubPedido(subPedidoSalvo));
        
        // Registra evento
        eventLogService.registrarEventoSubPedido(
            subPedidoSalvo, null, StatusSubPedido.CRIADO, 
            null, "SubPedido criado", 0L);
        
        log.info("SubPedido criado - ID: {} (Status: CRIADO)", subPedidoSalvo.getId());
        return subPedidoSalvo;
    }

    /**
     * Busca SubPedido por ID
     */
    @Transactional(readOnly = true)
    public SubPedido buscarPorId(Long id) {
        return subPedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubPedido não encontrado"));
    }

    /**
     * MÁQUINA DE ESTADOS - Altera status do SubPedido
     * 
     * VALIDAÇÕES:
     * 1. Transição é válida?
     * 2. Usuário tem permissão?
     * 3. Motivo fornecido (se cancelamento)?
     * 
     * IDEMPOTÊNCIA:
     * - Mesmo estado → retorna sucesso sem alteração
     * 
     * AUDITORIA:
     * - Gera EventLog
     * - Atualiza @Version (concorrência)
     * 
     * @param id ID do SubPedido
     * @param novoStatus Novo status desejado
     * @param motivo Motivo (obrigatório para CANCELADO)
     * @return SubPedido atualizado
     */
    @Transactional
    public SubPedido alterarStatus(Long id, StatusSubPedido novoStatus, String motivo) {
        long inicio = System.currentTimeMillis();
        
        // BUSCAR SubPedido (usa @Version para concorrência otimista)
        SubPedido subPedido = buscarPorId(id);
        StatusSubPedido estadoAtual = subPedido.getStatus();
        
        log.info("Alterando status SubPedido {} de {} para {}", id, estadoAtual, novoStatus);
        
        // IDEMPOTÊNCIA: já está no estado desejado
        if (estadoAtual == novoStatus) {
            log.debug("SubPedido {} já está em {}: operação idempotente", id, novoStatus);
            return subPedido;
        }
        
        // Obter roles do usuário autenticado
        Set<String> roles = obterRolesUsuarioAutenticado();
        
        // VALIDAR: transição + permissão
        transicaoValidator.validarTransicao(estadoAtual, novoStatus, roles);
        
        // VALIDAR: motivo de cancelamento
        if (novoStatus == StatusSubPedido.CANCELADO) {
            transicaoValidator.validarMotivoCancelamento(motivo);
        }
        
        // APLICAR TRANSIÇÃO
        subPedido.setStatus(novoStatus);
        LocalDateTime agora = LocalDateTime.now();
        
        // Atualizar timestamps conforme estado
        switch (novoStatus) {
            case PENDENTE -> { /* timestamp já definido no CRIADO */ }
            case EM_PREPARACAO -> subPedido.setIniciadoEm(agora);
            case PRONTO -> subPedido.setProntoEm(agora);
            case ENTREGUE -> subPedido.setEntregueEm(agora);
            case CANCELADO -> {
                // Adicionar motivo nas observações
                String obs = subPedido.getObservacoes() != null ? subPedido.getObservacoes() + " | " : "";
                subPedido.setObservacoes(obs + "CANCELADO: " + motivo);
            }
        }
        
        // PERSISTIR (atualiza @Version automaticamente)
        SubPedido subPedidoSalvo = subPedidoRepository.save(subPedido);
        
        // AUDITORIA: registrar evento
        long tempoTransacao = System.currentTimeMillis() - inicio;
        eventLogService.registrarEventoSubPedido(
            subPedidoSalvo, estadoAtual, novoStatus, 
            null, motivo != null ? motivo : "Transição de estado", 
            tempoTransacao);
        
        log.info("SubPedido {} atualizado: {} → {} ({}ms)", id, estadoAtual, novoStatus, tempoTransacao);
        
        // ⭐ CRÍTICO: Recalcular status do Pedido após mudança em SubPedido ⭐
        pedidoService.recalcularStatusPedido(subPedidoSalvo.getPedido().getId());
        
        return subPedidoSalvo;
    }

    /**
     * COZINHA assume SubPedido (PENDENTE → EM_PREPARACAO)
     */
    @Transactional
    public SubPedido assumir(Long id) {
        log.info("Cozinha assumindo SubPedido {}", id);
        SubPedido subPedido = buscarPorId(id);
        
        // Define responsável (contexto de segurança ou SYSTEM para testes)
        try {
            String responsavel = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
            subPedido.setResponsavelPreparo(responsavel);
        } catch (Exception e) {
            subPedido.setResponsavelPreparo("SYSTEM");
        }
        
        return alterarStatus(id, StatusSubPedido.EM_PREPARACAO, "Assumido pela cozinha");
    }

    /**
     * COZINHA marca SubPedido como PRONTO (EM_PREPARACAO → PRONTO)
     */
    @Transactional
    public SubPedido marcarPronto(Long id) {
        log.info("Marcando SubPedido {} como PRONTO", id);
        return alterarStatus(id, StatusSubPedido.PRONTO, "Preparação finalizada");
    }

    /**
     * ATENDENTE marca SubPedido como ENTREGUE (PRONTO → ENTREGUE)
     */
    @Transactional
    public SubPedido marcarEntregue(Long id) {
        log.info("Marcando SubPedido {} como ENTREGUE", id);
        return alterarStatus(id, StatusSubPedido.ENTREGUE, "Entregue ao cliente");
    }

    /**
     * GERENTE/ADMIN cancela SubPedido (qualquer → CANCELADO)
     * Motivo é OBRIGATÓRIO
     */
    @Transactional
    public SubPedido cancelar(Long id, String motivo) {
        log.info("Cancelando SubPedido {} - Motivo: {}", id, motivo);
        return alterarStatus(id, StatusSubPedido.CANCELADO, motivo);
    }

    /**
     * Confirma SubPedido (CRIADO → PENDENTE)
     * Usado internamente após criação
     */
    @Transactional
    public SubPedido confirmar(Long id) {
        log.info("Confirmando SubPedido {}", id);
        return alterarStatus(id, StatusSubPedido.PENDENTE, "SubPedido confirmado");
    }

    /**
     * Obtém roles do usuário autenticado via SecurityContext
     */
    private Set<String> obterRolesUsuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException("Usuário não autenticado");
        }
        
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
    }

    /**
     * Busca SubPedidos de um Pedido
     */
    @Transactional(readOnly = true)
    public List<SubPedido> buscarPorPedido(Long pedidoId) {
        return subPedidoRepository.findByPedidoIdOrderByCreatedAtAsc(pedidoId);
    }

    /**
     * Busca SubPedidos ativos de uma Cozinha
     */
    @Transactional(readOnly = true)
    public List<SubPedido> buscarAtivosPorCozinha(Long cozinhaId) {
        return subPedidoRepository.findSubPedidosAtivosByCozinha(cozinhaId);
    }

    /**
     * Busca SubPedidos prontos de uma Cozinha (para impressão)
     */
    @Transactional(readOnly = true)
    public List<SubPedido> buscarProntosPorCozinha(Long cozinhaId) {
        return subPedidoRepository.findByCozinhaIdAndStatusOrderByCreatedAtAsc(cozinhaId, StatusSubPedido.PRONTO);
    }

    /**
     * Busca SubPedidos com atraso
     */
    @Transactional(readOnly = true)
    public List<SubPedido> buscarComAtraso(int minutosAtraso) {
        LocalDateTime tempoLimite = LocalDateTime.now().minusMinutes(minutosAtraso);
        return subPedidoRepository.findSubPedidosComAtraso(tempoLimite);
    }

    /**
     * KPI: Tempo médio de preparação por cozinha
     */
    @Transactional(readOnly = true)
    public Map<String, Double> calcularTempoMedioPorCozinha() {
        Map<String, Double> resultado = new HashMap<>();
        
        List<Cozinha> cozinhas = cozinhaRepository.findAll();
        for (Cozinha cozinha : cozinhas) {
            List<SubPedido> concluidos = subPedidoRepository.findByCozinhaIdAndStatusOrderByCreatedAtAsc(
                cozinha.getId(), StatusSubPedido.ENTREGUE
            );
            
            if (!concluidos.isEmpty()) {
                double mediaMinutos = concluidos.stream()
                    .mapToLong(SubPedido::calcularTempoPreparacao)
                    .average()
                    .orElse(0.0);
                
                resultado.put(cozinha.getNome(), mediaMinutos);
            }
        }
        
        return resultado;
    }

    /**
     * Conta SubPedidos ativos por cozinha
     */
    @Transactional(readOnly = true)
    public long contarAtivosPorCozinha(Long cozinhaId) {
        return subPedidoRepository.contarSubPedidosAtivosPorCozinha(cozinhaId);
    }
}
