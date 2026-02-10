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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service para operações de negócio com SubPedido
 * 
 * SubPedido é a UNIDADE OPERACIONAL de execução
 * Permite trabalho paralelo de múltiplas cozinhas e entrega parcial
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubPedidoService {

    private final SubPedidoRepository subPedidoRepository;
    private final CozinhaRepository cozinhaRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final EventLogService eventLogService;

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
     * Cria SubPedido
     */
    @Transactional
    public SubPedido criar(Pedido pedido, Cozinha cozinha, UnidadeAtendimento unidadeAtendimento, 
                          List<ItemPedido> itens, String observacoes) {
        log.info("Criando SubPedido para Pedido {} - Cozinha: {}", pedido.getNumero(), cozinha.getNome());

        SubPedido subPedido = SubPedido.builder()
                .pedido(pedido)
                .cozinha(cozinha)
                .unidadeAtendimento(unidadeAtendimento)
                .status(StatusSubPedido.PENDENTE)
                .observacoes(observacoes)
                .build();

        SubPedido subPedidoSalvo = subPedidoRepository.save(subPedido);
        
        // Vincula itens ao SubPedido
        itens.forEach(item -> item.setSubPedido(subPedidoSalvo));
        
        log.info("SubPedido criado - ID: {}", subPedidoSalvo.getId());
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
     * Avança status do SubPedido
     */
    @Transactional
    public SubPedido avancarStatus(Long id) {
        log.info("Avançando status do SubPedido ID: {}", id);
        
        long inicio = System.currentTimeMillis();
        SubPedido subPedido = buscarPorId(id);
        StatusSubPedido statusAtual = subPedido.getStatus();
        
        // Validar transição
        StatusSubPedido proximoStatus = switch (statusAtual) {
            case PENDENTE -> StatusSubPedido.RECEBIDO;
            case RECEBIDO -> StatusSubPedido.EM_PREPARACAO;
            case EM_PREPARACAO -> StatusSubPedido.PRONTO;
            case PRONTO -> StatusSubPedido.ENTREGUE;
            case ENTREGUE, CANCELADO -> throw new BusinessException(
                "SubPedido já está em estado final: " + statusAtual
            );
        };
        
        if (!subPedido.podeAvancarPara(proximoStatus)) {
            throw new BusinessException(
                "Transição inválida de " + statusAtual + " para " + proximoStatus
            );
        }
        
        // Atualizar status e timestamps
        subPedido.setStatus(proximoStatus);
        LocalDateTime agora = LocalDateTime.now();
        
        switch (proximoStatus) {
            case RECEBIDO -> subPedido.setRecebidoEm(agora);
            case EM_PREPARACAO -> subPedido.setIniciadoEm(agora);
            case PRONTO -> subPedido.setProntoEm(agora);
            case ENTREGUE -> subPedido.setEntregueEm(agora);
        }
        
        SubPedido subPedidoSalvo = subPedidoRepository.save(subPedido);
        
        // Registra evento de auditoria
        long tempoTransacao = System.currentTimeMillis() - inicio;
        eventLogService.registrarEventoSubPedido(
            subPedidoSalvo, statusAtual, proximoStatus, null, 
            "Avanço automático", tempoTransacao);
        
        log.info("Status do SubPedido {} atualizado: {} -> {}", id, statusAtual, proximoStatus);
        
        return subPedidoSalvo;
    }

    /**
     * Cancela SubPedido
     */
    @Transactional
    public SubPedido cancelar(Long id, String motivo) {
        log.info("Cancelando SubPedido ID: {} - Motivo: {}", id, motivo);
        
        SubPedido subPedido = buscarPorId(id);
        
        if (subPedido.getStatus() == StatusSubPedido.ENTREGUE) {
            throw new BusinessException("Não é possível cancelar SubPedido já entregue");
        }
        
        if (subPedido.getStatus() == StatusSubPedido.CANCELADO) {
            throw new BusinessException("SubPedido já está cancelado");
        }
        
        StatusSubPedido statusAnterior = subPedido.getStatus();
        subPedido.setStatus(StatusSubPedido.CANCELADO);
        subPedido.setObservacoes(
            (subPedido.getObservacoes() != null ? subPedido.getObservacoes() + " | " : "") +
            "CANCELADO: " + motivo
        );
        
        SubPedido subPedidoSalvo = subPedidoRepository.save(subPedido);
        
        // Registra evento de auditoria
        eventLogService.registrarEventoSubPedido(
            subPedidoSalvo, statusAnterior, StatusSubPedido.CANCELADO, 
            null, motivo, null);
        
        return subPedidoSalvo;
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
