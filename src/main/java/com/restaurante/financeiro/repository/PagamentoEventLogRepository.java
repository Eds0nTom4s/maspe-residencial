package com.restaurante.financeiro.repository;

import com.restaurante.financeiro.enums.TipoEventoFinanceiro;
import com.restaurante.model.entity.PagamentoEventLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository para PagamentoEventLog (auditoria)
 */
@Repository
public interface PagamentoEventLogRepository extends JpaRepository<PagamentoEventLog, Long> {
    
    /**
     * Busca eventos de um pagamento
     */
    List<PagamentoEventLog> findByPagamentoIdOrderByTimestampDesc(Long pagamentoId);
    
    /**
     * Busca eventos de um pedido
     */
    List<PagamentoEventLog> findByPedidoIdOrderByTimestampDesc(Long pedidoId);
    
    /**
     * Busca eventos por tipo
     */
    List<PagamentoEventLog> findByTipoEventoOrderByTimestampDesc(TipoEventoFinanceiro tipoEvento);
    
    /**
     * Busca eventos de um usuário
     */
    Page<PagamentoEventLog> findByUsuarioOrderByTimestampDesc(String usuario, Pageable pageable);
    
    /**
     * Busca eventos em período
     */
    @Query("SELECT e FROM PagamentoEventLog e WHERE e.timestamp BETWEEN :inicio AND :fim " +
           "ORDER BY e.timestamp DESC")
    List<PagamentoEventLog> findEventosPeriodo(LocalDateTime inicio, LocalDateTime fim);
    
    /**
     * Busca estornos (auditoria crítica)
     */
    @Query("SELECT e FROM PagamentoEventLog e WHERE e.tipoEvento IN ('ESTORNO_MANUAL', 'ESTORNO_AUTOMATICO') " +
           "ORDER BY e.timestamp DESC")
    List<PagamentoEventLog> findEstornos();
    
    /**
     * Busca autorizações pós-pago
     */
    @Query("SELECT e FROM PagamentoEventLog e WHERE e.tipoEvento IN ('AUTORIZACAO_POS_PAGO', 'NEGACAO_POS_PAGO') " +
           "ORDER BY e.timestamp DESC")
    List<PagamentoEventLog> findAutorizacoesPosPago();
}
