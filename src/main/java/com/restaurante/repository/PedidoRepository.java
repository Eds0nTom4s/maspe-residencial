package com.restaurante.repository;

import com.restaurante.model.entity.Pedido;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.TipoPagamentoPedido;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository para operações de banco de dados com Pedido
 */
@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    /**
     * Busca pedido por número
     */
    Optional<Pedido> findByNumero(String numero);

    /**
     * Busca pedidos por status com paginação
     */
    Page<Pedido> findByStatus(StatusPedido status, Pageable pageable);

    /**
     * Busca pedidos pendentes e recebidos com paginação (para atendente)
     */
    Page<Pedido> findByStatusIn(List<StatusPedido> status, Pageable pageable);

    /**
     * Busca pedidos de hoje por status com paginação
     */
    @Query("SELECT p FROM Pedido p WHERE CAST(p.createdAt AS date) = CURRENT_DATE AND p.status = :status")
    Page<Pedido> findPedidosDeHojePorStatus(StatusPedido status, Pageable pageable);

    /**
     * Busca pedidos pós-pago abertos (não pagos) por sessão de consumo.
     */
    List<Pedido> findBySessaoConsumoIdAndTipoPagamentoAndStatusFinanceiro(
        Long sessaoConsumoId,
        TipoPagamentoPedido tipoPagamento,
        StatusFinanceiroPedido statusFinanceiro
    );

    /**
     * Busca pedido por ID com SubPedidos carregados (JOIN FETCH)
     * ⚠ Necessário para evitar lazy loading de subPedidos
     */
    @Query("SELECT p FROM Pedido p LEFT JOIN FETCH p.subPedidos WHERE p.id = :id")
    Optional<Pedido> findByIdComSubPedidos(@Param("id") Long id);

    /**
     * Lista todos os pedidos de uma SessaoConsumo com paginação.
     */
    Page<Pedido> findBySessaoConsumoId(Long sessaoConsumoId, Pageable pageable);

    /**
     * Lista pedidos activos (CRIADO ou EM_ANDAMENTO) de uma SessaoConsumo com paginação.
     */
    Page<Pedido> findBySessaoConsumoIdAndStatusIn(
            Long sessaoConsumoId, List<StatusPedido> statuses, Pageable pageable);

    /**
     * Lista pedidos de uma SessaoConsumo por status, ordenados por data de criação (unpaged).
     * Usado para validações de encerramento de sessão.
     */
    List<Pedido> findBySessaoConsumoIdAndStatusInOrderByCreatedAtAsc(
            Long sessaoConsumoId, List<StatusPedido> statuses);

    // ─────────────────────────────────────────────────────────────────────────
    // Queries para Dashboard e Relatórios
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Conta pedidos do dia atual por status.
     * Usado no dashboard para totalPedidosHoje e pedidosPendentes.
     */
    @Query("SELECT COUNT(p) FROM Pedido p WHERE CAST(p.createdAt AS date) = CURRENT_DATE AND p.status IN :statuses")
    long countPedidosHojePorStatuses(@Param("statuses") List<StatusPedido> statuses);

    /**
     * Conta todos os pedidos do dia atual.
     */
    @Query("SELECT COUNT(p) FROM Pedido p WHERE CAST(p.createdAt AS date) = CURRENT_DATE")
    long countPedidosHoje();

    /**
     * Soma TOTAL de pedidos finalizados hoje (receita do dia).
     * Considera apenas pedidos FINALIZADOS (completos).
     */
    @Query("SELECT COALESCE(SUM(p.total), 0) FROM Pedido p " +
           "WHERE CAST(p.createdAt AS date) = CURRENT_DATE AND p.status = 'FINALIZADO'")
    BigDecimal calcularReceitaHoje();

    /**
     * Soma TOTAL de pedidos finalizados num período.
     */
    @Query("SELECT COALESCE(SUM(p.total), 0) FROM Pedido p " +
           "WHERE p.createdAt BETWEEN :inicio AND :fim AND p.status = 'FINALIZADO'")
    BigDecimal calcularReceitaPorPeriodo(@Param("inicio") LocalDateTime inicio,
                                         @Param("fim") LocalDateTime fim);

    /**
     * Lista pedidos filtrados por período com paginação (para o painel admin).
     * Todos os parâmetros são opcionais — null ignora o filtro.
     */
    @Query("SELECT p FROM Pedido p " +
           "WHERE (:status IS NULL OR p.status = :status) " +
           "AND (:inicio IS NULL OR p.createdAt >= :inicio) " +
           "AND (:fim IS NULL OR p.createdAt <= :fim) " +
           "AND (:sessaoId IS NULL OR p.sessaoConsumo.id = :sessaoId)")
    Page<Pedido> findComFiltros(@Param("status") StatusPedido status,
                                @Param("inicio") LocalDateTime inicio,
                                @Param("fim") LocalDateTime fim,
                                @Param("sessaoId") Long sessaoId,
                                Pageable pageable);

    /**
     * Lista todos os pedidos do dia atual com paginação.
     */
    @Query("SELECT p FROM Pedido p WHERE CAST(p.createdAt AS date) = CURRENT_DATE")
    Page<Pedido> findPedidosDeHoje(Pageable pageable);

    /**
     * Conta pedidos por status ativo no período (para relatórios).
     */
    @Query("SELECT COUNT(p) FROM Pedido p " +
           "WHERE p.createdAt BETWEEN :inicio AND :fim AND p.status = :status")
    long countPorStatusEPeriodo(@Param("status") StatusPedido status,
                                @Param("inicio") LocalDateTime inicio,
                                @Param("fim") LocalDateTime fim);
}

