package com.restaurante.repository;

import com.restaurante.model.entity.TransacaoFundo;
import com.restaurante.model.enums.TipoTransacaoFundo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransacaoFundoRepository extends JpaRepository<TransacaoFundo, Long> {

    /**
     * Histórico de transações de um fundo com paginação
     */
    Page<TransacaoFundo> findByFundoConsumoIdOrderByCreatedAtDesc(Long fundoConsumoId, Pageable pageable);

    /**
     * Histórico de transações em período
     */
    @Query("SELECT t FROM TransacaoFundo t WHERE t.fundoConsumo.id = :fundoConsumoId " +
           "AND t.createdAt BETWEEN :dataInicio AND :dataFim " +
           "ORDER BY t.createdAt DESC")
    List<TransacaoFundo> findByFundoConsumoIdAndPeriodo(Long fundoConsumoId, LocalDateTime dataInicio, LocalDateTime dataFim);

    /**
     * Busca transação de débito por pedido
     */
    Optional<TransacaoFundo> findByPedidoIdAndTipo(Long pedidoId, TipoTransacaoFundo tipo);

    /**
     * Verifica se pedido já foi debitado
     */
    boolean existsByPedidoIdAndTipo(Long pedidoId, TipoTransacaoFundo tipo);

    /**
     * Saldo agregado do fundo a partir do ledger append-only.
     */
    @Query("""
        SELECT COALESCE(SUM(
            CASE
                WHEN t.tipo = com.restaurante.model.enums.TipoTransacaoFundo.DEBITO THEN -t.valor
                ELSE t.valor
            END
        ), 0)
        FROM TransacaoFundo t
        WHERE t.fundoConsumo.id = :fundoConsumoId
    """)
    java.math.BigDecimal calcularSaldoAgregado(Long fundoConsumoId);

    /**
     * Transações de crédito recentes para o dashboard.
     */
    @Query("""
        SELECT t
        FROM TransacaoFundo t
        WHERE t.tipo = com.restaurante.model.enums.TipoTransacaoFundo.CREDITO
        ORDER BY t.createdAt DESC
    """)
    List<TransacaoFundo> findRecentPayments(Pageable pageable);
}
