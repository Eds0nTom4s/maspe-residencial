package com.restaurante.repository;

import com.restaurante.model.entity.ConfiguracaoFinanceiraEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ConfiguracaoFinanceiraEventLogRepository
        extends JpaRepository<ConfiguracaoFinanceiraEventLog, Long> {

    /**
     * Histórico completo por tipo de evento com paginação.
     */
    Page<ConfiguracaoFinanceiraEventLog> findByTipoEvento(String tipoEvento, Pageable pageable);

    /**
     * Todos os eventos de um operador específico com paginação.
     */
    Page<ConfiguracaoFinanceiraEventLog> findByUsuarioNome(String usuarioNome, Pageable pageable);

    /**
     * Eventos num intervalo de tempo com paginação.
     */
    @Query("SELECT e FROM ConfiguracaoFinanceiraEventLog e WHERE e.timestamp BETWEEN :inicio AND :fim")
    Page<ConfiguracaoFinanceiraEventLog> findByPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, Pageable pageable);

    /**
     * Busca os últimos N eventos (unpaged fallback).
     */
    @Query(value = "SELECT * FROM configuracao_financeira_event_logs ORDER BY timestamp DESC LIMIT :limite", nativeQuery = true)
    List<ConfiguracaoFinanceiraEventLog> findUltimosEventos(@Param("limite") int limite);

    /** Contagem de eventos agrupada por tipo. */
    @Query("SELECT e.tipoEvento, COUNT(e) FROM ConfiguracaoFinanceiraEventLog e GROUP BY e.tipoEvento")
    List<Object[]> countByTipoEvento();

    /** Tipos de evento distintos registrados (para listar módulos/ações disponíveis). */
    @Query("SELECT DISTINCT e.tipoEvento FROM ConfiguracaoFinanceiraEventLog e ORDER BY e.tipoEvento")
    List<String> findTiposEventoDistintos();

    /** Contagem total de eventos. */
    @Query("SELECT COUNT(e) FROM ConfiguracaoFinanceiraEventLog e")
    long countTotal();

    /** Contagem de eventos nas últimas N horas. */
    @Query("SELECT COUNT(e) FROM ConfiguracaoFinanceiraEventLog e WHERE e.timestamp >= :desde")
    long countDesde(@Param("desde") LocalDateTime desde);
}

