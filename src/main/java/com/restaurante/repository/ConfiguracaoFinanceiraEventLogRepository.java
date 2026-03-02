package com.restaurante.repository;

import com.restaurante.model.entity.ConfiguracaoFinanceiraEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ConfiguracaoFinanceiraEventLogRepository
        extends JpaRepository<ConfiguracaoFinanceiraEventLog, Long> {

    /** Histórico completo por tipo de evento, ordenado do mais recente. */
    List<ConfiguracaoFinanceiraEventLog> findByTipoEventoOrderByTimestampDesc(String tipoEvento);

    /** Todos os eventos de um operador específico. */
    List<ConfiguracaoFinanceiraEventLog> findByUsuarioNomeOrderByTimestampDesc(String usuarioNome);

    /** Eventos num intervalo de tempo. */
    @Query("SELECT e FROM ConfiguracaoFinanceiraEventLog e " +
           "WHERE e.timestamp BETWEEN :inicio AND :fim " +
           "ORDER BY e.timestamp DESC")
    List<ConfiguracaoFinanceiraEventLog> findByPeriodo(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim")    LocalDateTime fim);

    /** Últimos N eventos (auditoria rápida do dashboard). */
    @Query("SELECT e FROM ConfiguracaoFinanceiraEventLog e ORDER BY e.timestamp DESC LIMIT :limite")
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
    long countDesde(@Param("desde") java.time.LocalDateTime desde);
}
