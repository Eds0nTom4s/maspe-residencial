package com.restaurante.service;

import com.restaurante.model.entity.*;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.repository.PedidoEventLogRepository;
import com.restaurante.repository.SubPedidoEventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service para gerenciar Event Logs de auditoria
 * Registra e consulta histórico de mudanças em Pedidos e SubPedidos
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventLogService {

    private final PedidoEventLogRepository pedidoEventLogRepository;
    private final SubPedidoEventLogRepository subPedidoEventLogRepository;

    // ========== PEDIDO EVENT LOG ==========

    /**
     * Registra evento de mudança de status em Pedido
     */
    @Transactional
    public PedidoEventLog registrarEventoPedido(
            Pedido pedido,
            StatusPedido statusAnterior,
            StatusPedido statusNovo,
            String usuario,
            String observacoes) {
        
        log.debug("Registrando evento de Pedido {} - {} -> {}", 
            pedido.getNumero(), statusAnterior, statusNovo);

        PedidoEventLog evento = PedidoEventLog.builder()
                .pedido(pedido)
                .statusAnterior(statusAnterior)
                .statusNovo(statusNovo)
                .usuario(usuario != null ? usuario : "system")
                .timestamp(LocalDateTime.now())
                .observacoes(observacoes)
                .build();

        return pedidoEventLogRepository.save(evento);
    }

    /**
     * Busca histórico completo de um pedido
     */
    @Transactional(readOnly = true)
    public List<PedidoEventLog> buscarHistoricoPedido(Long pedidoId) {
        log.debug("Buscando histórico do pedido ID: {}", pedidoId);
        return pedidoEventLogRepository.findByPedidoIdOrderByTimestampAsc(pedidoId);
    }

    /**
     * Busca eventos de pedidos por usuário
     */
    @Transactional(readOnly = true)
    public List<PedidoEventLog> buscarEventosPedidoPorUsuario(String usuario) {
        log.debug("Buscando eventos de pedidos do usuário: {}", usuario);
        return pedidoEventLogRepository.findByUsuarioOrderByTimestampDesc(usuario);
    }

    /**
     * Busca eventos de pedidos em período
     */
    @Transactional(readOnly = true)
    public List<PedidoEventLog> buscarEventosPedidoPorPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        log.debug("Buscando eventos de pedidos entre {} e {}", inicio, fim);
        return pedidoEventLogRepository.findByPeriodo(inicio, fim);
    }

    /**
     * Busca eventos recentes de pedidos (últimas N horas)
     */
    @Transactional(readOnly = true)
    public List<PedidoEventLog> buscarEventosPedidoRecentes(int horas) {
        LocalDateTime desde = LocalDateTime.now().minusHours(horas);
        return pedidoEventLogRepository.findEventosRecentes(desde);
    }

    /**
     * Busca eventos de mudança para status específico
     */
    @Transactional(readOnly = true)
    public List<PedidoEventLog> buscarEventosPedidoPorStatus(StatusPedido status) {
        return pedidoEventLogRepository.findByStatusNovoOrderByTimestampDesc(status);
    }

    // ========== SUBPEDIDO EVENT LOG ==========

    /**
     * Registra evento de mudança de status em SubPedido
     */
    @Transactional
    public SubPedidoEventLog registrarEventoSubPedido(
            SubPedido subPedido,
            StatusSubPedido statusAnterior,
            StatusSubPedido statusNovo,
            String usuario,
            String observacoes,
            Long tempoTransacaoMs) {
        
        log.debug("Registrando evento de SubPedido {} - {} -> {}", 
            subPedido.getId(), statusAnterior, statusNovo);

        SubPedidoEventLog evento = SubPedidoEventLog.builder()
                .subPedido(subPedido)
                .cozinha(subPedido.getCozinha())
                .statusAnterior(statusAnterior)
                .statusNovo(statusNovo)
                .usuario(usuario != null ? usuario : "system")
                .timestamp(LocalDateTime.now())
                .observacoes(observacoes)
                .tempoTransacaoMs(tempoTransacaoMs)
                .build();

        SubPedidoEventLog eventoSalvo = subPedidoEventLogRepository.save(evento);

        // Se for transição crítica, logar em nível INFO
        if (eventoSalvo.isTransicaoCritica()) {
            log.info("Evento crítico registrado: {} - {}", 
                eventoSalvo.getDescricao(), subPedido.getPedido().getNumero());
        }

        return eventoSalvo;
    }

    /**
     * Busca histórico completo de um subpedido
     */
    @Transactional(readOnly = true)
    public List<SubPedidoEventLog> buscarHistoricoSubPedido(Long subPedidoId) {
        log.debug("Buscando histórico do subpedido ID: {}", subPedidoId);
        return subPedidoEventLogRepository.findBySubPedidoIdOrderByTimestampAsc(subPedidoId);
    }

    /**
     * Busca todos eventos de um pedido (através dos subpedidos)
     */
    @Transactional(readOnly = true)
    public List<SubPedidoEventLog> buscarHistoricoSubPedidosPorPedido(Long pedidoId) {
        log.debug("Buscando histórico de subpedidos do pedido ID: {}", pedidoId);
        return subPedidoEventLogRepository.findByPedidoId(pedidoId);
    }

    /**
     * Busca eventos de uma cozinha específica
     */
    @Transactional(readOnly = true)
    public List<SubPedidoEventLog> buscarEventosSubPedidoPorCozinha(Long cozinhaId) {
        log.debug("Buscando eventos de subpedidos da cozinha ID: {}", cozinhaId);
        return subPedidoEventLogRepository.findByCozinhaIdOrderByTimestampDesc(cozinhaId);
    }

    /**
     * Busca eventos de uma cozinha em período
     */
    @Transactional(readOnly = true)
    public List<SubPedidoEventLog> buscarEventosSubPedidoPorCozinhaEPeriodo(
            Long cozinhaId, LocalDateTime inicio, LocalDateTime fim) {
        return subPedidoEventLogRepository.findByCozinhaIdAndPeriodo(cozinhaId, inicio, fim);
    }

    /**
     * Busca eventos de subpedidos por usuário
     */
    @Transactional(readOnly = true)
    public List<SubPedidoEventLog> buscarEventosSubPedidoPorUsuario(String usuario) {
        log.debug("Buscando eventos de subpedidos do usuário: {}", usuario);
        return subPedidoEventLogRepository.findByUsuarioOrderByTimestampDesc(usuario);
    }

    /**
     * Busca eventos de subpedidos em período
     */
    @Transactional(readOnly = true)
    public List<SubPedidoEventLog> buscarEventosSubPedidoPorPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        log.debug("Buscando eventos de subpedidos entre {} e {}", inicio, fim);
        return subPedidoEventLogRepository.findByPeriodo(inicio, fim);
    }

    /**
     * Busca eventos críticos recentes (PRONTO, ENTREGUE)
     */
    @Transactional(readOnly = true)
    public List<SubPedidoEventLog> buscarEventosCriticosRecentes(int horas) {
        LocalDateTime desde = LocalDateTime.now().minusHours(horas);
        return subPedidoEventLogRepository.findEventosCriticosRecentes(desde);
    }

    /**
     * Busca eventos de mudança para status específico
     */
    @Transactional(readOnly = true)
    public List<SubPedidoEventLog> buscarEventosSubPedidoPorStatus(StatusSubPedido status) {
        return subPedidoEventLogRepository.findByStatusNovoOrderByTimestampDesc(status);
    }

    // ========== ESTATÍSTICAS ==========

    /**
     * Calcula tempo médio de transação de uma cozinha
     */
    @Transactional(readOnly = true)
    public Double calcularTempoMedioTransacao(Long cozinhaId) {
        return subPedidoEventLogRepository.calcularTempoMedioTransacao(cozinhaId);
    }

    /**
     * Conta total de eventos de um pedido (pedido + subpedidos)
     */
    @Transactional(readOnly = true)
    public Long contarEventosPedido(Long pedidoId) {
        Long eventosPedido = pedidoEventLogRepository.countByPedidoId(pedidoId);
        List<SubPedidoEventLog> eventosSubPedidos = subPedidoEventLogRepository.findByPedidoId(pedidoId);
        return eventosPedido + eventosSubPedidos.size();
    }

    /**
     * Busca timeline completa de um pedido (pedido + subpedidos ordenados)
     */
    @Transactional(readOnly = true)
    public List<String> buscarTimelineCompletaPedido(Long pedidoId) {
        List<PedidoEventLog> eventosPedido = pedidoEventLogRepository.findByPedidoIdOrderByTimestampAsc(pedidoId);
        List<SubPedidoEventLog> eventosSubPedidos = subPedidoEventLogRepository.findByPedidoId(pedidoId);

        // Combina e ordena todos eventos por timestamp
        return Stream.concat(
            eventosPedido.stream().map(e -> 
                String.format("[%s] %s - %s por %s", 
                    e.getTimestamp(), e.getDescricao(), 
                    e.getObservacoes() != null ? e.getObservacoes() : "", 
                    e.getUsuario())),
            eventosSubPedidos.stream().map(e -> 
                String.format("[%s] %s - %s por %s", 
                    e.getTimestamp(), e.getDescricao(), 
                    e.getObservacoes() != null ? e.getObservacoes() : "", 
                    e.getUsuario()))
        )
        .sorted()
        .collect(Collectors.toList());
    }
}
