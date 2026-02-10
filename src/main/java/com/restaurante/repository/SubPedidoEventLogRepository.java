package com.restaurante.repository;

import com.restaurante.model.entity.SubPedidoEventLog;
import com.restaurante.model.enums.StatusSubPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository para SubPedidoEventLog
 */
@Repository
public interface SubPedidoEventLogRepository extends JpaRepository<SubPedidoEventLog, Long> {

    /**
     * Busca todos eventos de um subpedido específico ordenados por timestamp
     */
    List<SubPedidoEventLog> findBySubPedidoIdOrderByTimestampAsc(Long subPedidoId);

    /**
     * Busca eventos de uma cozinha específica
     */
    List<SubPedidoEventLog> findByCozinhaIdOrderByTimestampDesc(Long cozinhaId);

    /**
     * Busca eventos por usuário
     */
    List<SubPedidoEventLog> findByUsuarioOrderByTimestampDesc(String usuario);

    /**
     * Busca eventos em um período
     */
    @Query("SELECT sel FROM SubPedidoEventLog sel WHERE sel.timestamp BETWEEN :inicio AND :fim ORDER BY sel.timestamp DESC")
    List<SubPedidoEventLog> findByPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /**
     * Busca eventos de mudança para status específico
     */
    List<SubPedidoEventLog> findByStatusNovoOrderByTimestampDesc(StatusSubPedido status);

    /**
     * Busca eventos críticos (PRONTO, ENTREGUE) recentes
     */
    @Query("SELECT sel FROM SubPedidoEventLog sel WHERE sel.statusNovo IN ('PRONTO', 'ENTREGUE') " +
           "AND sel.timestamp >= :desde ORDER BY sel.timestamp DESC")
    List<SubPedidoEventLog> findEventosCriticosRecentes(@Param("desde") LocalDateTime desde);

    /**
     * Busca eventos de uma cozinha em período
     */
    @Query("SELECT sel FROM SubPedidoEventLog sel WHERE sel.cozinha.id = :cozinhaId " +
           "AND sel.timestamp BETWEEN :inicio AND :fim ORDER BY sel.timestamp ASC")
    List<SubPedidoEventLog> findByCozinhaIdAndPeriodo(
        @Param("cozinhaId") Long cozinhaId,
        @Param("inicio") LocalDateTime inicio,
        @Param("fim") LocalDateTime fim);

    /**
     * Conta eventos de um subpedido
     */
    Long countBySubPedidoId(Long subPedidoId);

    /**
     * Busca eventos por pedido (via subpedido)
     */
    @Query("SELECT sel FROM SubPedidoEventLog sel WHERE sel.subPedido.pedido.id = :pedidoId ORDER BY sel.timestamp ASC")
    List<SubPedidoEventLog> findByPedidoId(@Param("pedidoId") Long pedidoId);

    /**
     * Calcula tempo médio de transição por cozinha
     */
    @Query("SELECT AVG(sel.tempoTransacaoMs) FROM SubPedidoEventLog sel " +
           "WHERE sel.cozinha.id = :cozinhaId AND sel.tempoTransacaoMs IS NOT NULL")
    Double calcularTempoMedioTransacao(@Param("cozinhaId") Long cozinhaId);
}
