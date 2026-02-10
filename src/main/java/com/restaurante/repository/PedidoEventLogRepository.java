package com.restaurante.repository;

import com.restaurante.model.entity.PedidoEventLog;
import com.restaurante.model.enums.StatusPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository para PedidoEventLog
 */
@Repository
public interface PedidoEventLogRepository extends JpaRepository<PedidoEventLog, Long> {

    /**
     * Busca todos eventos de um pedido específico ordenados por timestamp
     */
    List<PedidoEventLog> findByPedidoIdOrderByTimestampAsc(Long pedidoId);

    /**
     * Busca eventos por usuário
     */
    List<PedidoEventLog> findByUsuarioOrderByTimestampDesc(String usuario);

    /**
     * Busca eventos em um período
     */
    @Query("SELECT pel FROM PedidoEventLog pel WHERE pel.timestamp BETWEEN :inicio AND :fim ORDER BY pel.timestamp DESC")
    List<PedidoEventLog> findByPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    /**
     * Busca eventos de mudança para status específico
     */
    List<PedidoEventLog> findByStatusNovoOrderByTimestampDesc(StatusPedido status);

    /**
     * Busca eventos recentes (últimas N horas)
     */
    @Query("SELECT pel FROM PedidoEventLog pel WHERE pel.timestamp >= :desde ORDER BY pel.timestamp DESC")
    List<PedidoEventLog> findEventosRecentes(@Param("desde") LocalDateTime desde);

    /**
     * Conta eventos de um pedido
     */
    Long countByPedidoId(Long pedidoId);

    /**
     * Busca último evento de um pedido
     */
    @Query("SELECT pel FROM PedidoEventLog pel WHERE pel.pedido.id = :pedidoId ORDER BY pel.timestamp DESC")
    List<PedidoEventLog> findUltimoEventoPorPedido(@Param("pedidoId") Long pedidoId);
}
