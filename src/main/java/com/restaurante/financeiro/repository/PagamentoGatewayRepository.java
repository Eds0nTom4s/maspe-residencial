package com.restaurante.financeiro.repository;

import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.enums.TipoPagamentoFinanceiro;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

/**
 * Repository para Pagamento (gateway)
 */
@Repository
public interface PagamentoGatewayRepository extends JpaRepository<Pagamento, Long> {
    
    /**
     * Busca pagamento por referência externa (merchantTransactionId)
     * IDEMPOTÊNCIA: usar para verificar se já existe
     */
    Optional<Pagamento> findByExternalReference(String externalReference);
    
    /**
     * Verifica se já existe pagamento com essa referência
     */
    boolean existsByExternalReference(String externalReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Pagamento> findForUpdateByExternalReference(String externalReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Pagamento> findForUpdateById(Long id);

    Page<Pagamento> findByTenantId(Long tenantId, Pageable pageable);

    Optional<Pagamento> findByIdAndTenantId(Long id, Long tenantId);

    Page<Pagamento> findByTenantIdAndStatus(Long tenantId, StatusPagamentoGateway status, Pageable pageable);

    @Query("SELECT p FROM Pagamento p WHERE p.tenant.id = :tenantId " +
            "AND (:status IS NULL OR p.status = :status) " +
            "AND (:statusFinanceiroPedido IS NULL OR p.pedido.statusFinanceiro = :statusFinanceiroPedido) " +
            "AND (:externalReference IS NULL OR p.externalReference = :externalReference) " +
            "AND (:from IS NULL OR p.createdAt >= :from) " +
            "AND (:to IS NULL OR p.createdAt <= :to) " +
            "AND (:pedidoNumero IS NULL OR p.pedido.numero = :pedidoNumero)")
    Page<Pagamento> searchTenantPagamentos(
            Long tenantId,
            StatusPagamentoGateway status,
            StatusFinanceiroPedido statusFinanceiroPedido,
            String externalReference,
            LocalDateTime from,
            LocalDateTime to,
            String pedidoNumero,
            Pageable pageable
    );

    @Query("SELECT p FROM Pagamento p WHERE " +
            "(:status IS NULL OR p.status = :status) " +
            "AND (:statusFinanceiroPedido IS NULL OR p.pedido.statusFinanceiro = :statusFinanceiroPedido) " +
            "AND (:externalReference IS NULL OR p.externalReference = :externalReference) " +
            "AND (:from IS NULL OR p.createdAt >= :from) " +
            "AND (:to IS NULL OR p.createdAt <= :to) " +
            "AND (:pedidoNumero IS NULL OR p.pedido.numero = :pedidoNumero)")
    Page<Pagamento> searchPlatformPagamentos(
            StatusPagamentoGateway status,
            StatusFinanceiroPedido statusFinanceiroPedido,
            String externalReference,
            LocalDateTime from,
            LocalDateTime to,
            String pedidoNumero,
            Pageable pageable
    );

    @Query("SELECT p FROM Pagamento p WHERE p.status = 'PENDENTE' AND p.createdAt < :threshold")
    Page<Pagamento> findPendentesAntigos(LocalDateTime threshold, Pageable pageable);

    @Query("SELECT p FROM Pagamento p WHERE p.tenant.id = :tenantId AND p.status = 'PENDENTE' AND p.createdAt < :threshold")
    Page<Pagamento> findPendentesAntigosByTenant(Long tenantId, LocalDateTime threshold, Pageable pageable);

    @Query("SELECT COUNT(p) FROM Pagamento p WHERE p.status = 'PENDENTE' AND p.createdAt < :threshold")
    long countPendentesAntigos(LocalDateTime threshold);

    @Query("SELECT COUNT(p) FROM Pagamento p WHERE p.tenant.id = :tenantId AND p.status = 'PENDENTE' AND p.createdAt < :threshold")
    long countPendentesAntigosByTenant(Long tenantId, LocalDateTime threshold);

    long countByTenantIdAndCreatedAtBetween(Long tenantId, LocalDateTime start, LocalDateTime end);

    long countByTenantIdAndStatusAndCreatedAtBetween(Long tenantId, StatusPagamentoGateway status, LocalDateTime start, LocalDateTime end);

    long countByTenantIdAndStatus(Long tenantId, StatusPagamentoGateway status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Pagamento p WHERE p.tenant.id = :tenantId AND p.status = 'PENDENTE'")
    BigDecimal sumPendentesByTenant(Long tenantId);

    @Query("SELECT COUNT(DISTINCT p.tenant.id) FROM Pagamento p")
    long countDistinctTenants();

    @Query("SELECT COUNT(p) FROM Pagamento p WHERE p.createdAt BETWEEN :start AND :end")
    long countHoje(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(p) FROM Pagamento p WHERE p.status = 'CONFIRMADO' AND p.createdAt BETWEEN :start AND :end")
    long countConfirmadoHoje(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(p) FROM Pagamento p WHERE p.status = 'PENDENTE'")
    long countPendentes();

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Pagamento p WHERE p.status = 'CONFIRMADO' AND p.createdAt BETWEEN :start AND :end")
    BigDecimal sumConfirmadoHoje(LocalDateTime start, LocalDateTime end);
    
    /**
     * Busca pagamento por chargeId do gateway
     */
    Optional<Pagamento> findByGatewayChargeId(String gatewayChargeId);
    
    /**
     * Busca pagamentos de um pedido
     */
    List<Pagamento> findByPedidoIdOrderByCreatedAtDesc(Long pedidoId);
    
    /**
     * Busca pagamentos de um fundo de consumo
     */
    List<Pagamento> findByFundoConsumoIdOrderByCreatedAtDesc(Long fundoConsumoId);
    
    /**
     * Busca pagamento confirmado de um pedido
     */
    @Query("SELECT p FROM Pagamento p WHERE p.pedido.id = :pedidoId " +
           "AND p.status = 'CONFIRMADO' AND p.tipoPagamento = :tipo")
    Optional<Pagamento> findPagamentoConfirmadoPorPedido(Long pedidoId, TipoPagamentoFinanceiro tipo);
    
    /**
     * Busca pagamentos pendentes (para monitoramento)
     */
    List<Pagamento> findByStatusOrderByCreatedAtAsc(StatusPagamentoGateway status);

    @Query(value = """
            select p.id
            from pagamentos_gateway p
            where p.polling_enabled = true
              and p.status in ('PENDENTE')
              and p.external_reference is not null
              and p.gateway_charge_id is not null
              and (p.next_polling_attempt_at is null or p.next_polling_attempt_at <= :now)
              and p.polling_attempts < :maxAttempts
              and p.created_at <= :initialDelayThreshold
              and p.created_at >= :maxAgeThreshold
            order by coalesce(p.next_polling_attempt_at, p.created_at) asc
            limit :limit
            """, nativeQuery = true)
    List<Long> findEligibleIdsForPolling(LocalDateTime now,
                                        LocalDateTime initialDelayThreshold,
                                        LocalDateTime maxAgeThreshold,
                                        int maxAttempts,
                                        int limit);

    /**
     * Busca pagamentos em status PENDENTE vinculados a um fundo.
     * Usado pelo expirarComSeguranca() para bloquear expiração quando há
     * pagamentos AppyPay em curso (ex: referência bancária ainda não paga).
     */
    @Query("SELECT p FROM Pagamento p WHERE p.fundoConsumo.id = :fundoId " +
           "AND p.status = 'PENDENTE'")
    List<Pagamento> findPagamentosPendentesByFundoId(Long fundoId);
    
    /**
     * Busca último pagamento confirmado do fundo
     */
    @Query("SELECT p FROM Pagamento p WHERE p.fundoConsumo.id = :fundoId " +
           "AND p.status = 'CONFIRMADO' ORDER BY p.confirmedAt DESC")
    List<Pagamento> findUltimosPagamentosConfirmadosFundo(Long fundoId);

    long countByTenantIdAndPedidoTurnoOperacionalIdAndStatus(Long tenantId, Long turnoOperacionalId, StatusPagamentoGateway status);

    @Query("select coalesce(sum(p.amount), 0) from Pagamento p where p.tenant.id = :tenantId and p.pedido.turnoOperacional.id = :turnoOperacionalId and p.status = :status")
    BigDecimal sumByTenantIdAndTurnoOperacionalIdAndStatus(Long tenantId, Long turnoOperacionalId, StatusPagamentoGateway status);

    @Query("""
            select p
            from Pagamento p
              join fetch p.pedido ped
            where p.tenant.id = :tenantId
              and p.status = 'PENDENTE'
              and (:turnoId is null or ped.turnoOperacional.id = :turnoId)
              and (:pedidoId is null or ped.id = :pedidoId)
              and (:unidadeAtendimentoId is null or ped.sessaoConsumo.unidadeAtendimento.id = :unidadeAtendimentoId)
              and (:metodo is null or p.metodo = :metodo)
              and (:pollingStatus is null or p.pollingStatus = :pollingStatus)
              and (:hasError is null or (:hasError = true and p.pollingLastErrorCode is not null) or (:hasError = false and p.pollingLastErrorCode is null))
              and (:olderThan is null or p.createdAt <= :olderThan)
              and (:de is null or p.createdAt >= :de)
              and (:ate is null or p.createdAt <= :ate)
            """)
    Page<Pagamento> searchPendentesTenant(
            Long tenantId,
            Long turnoId,
            Long pedidoId,
            Long unidadeAtendimentoId,
            com.restaurante.financeiro.enums.MetodoPagamentoAppyPay metodo,
            com.restaurante.financeiro.enums.PagamentoPollingStatus pollingStatus,
            Boolean hasError,
            LocalDateTime olderThan,
            LocalDateTime de,
            LocalDateTime ate,
            Pageable pageable
    );

    @Query("""
            select p.tenant.id as tenantId, count(p) as cnt
            from Pagamento p
            where p.status = com.restaurante.financeiro.enums.StatusPagamentoGateway.PENDENTE
            group by p.tenant.id
            """)
    List<Object[]> countPendentesByTenant();

    @Query("""
            select p.tenant.id as tenantId, count(p) as cnt
            from Pagamento p
            where p.status = com.restaurante.financeiro.enums.StatusPagamentoGateway.PENDENTE
              and (p.pollingStatus = com.restaurante.financeiro.enums.PagamentoPollingStatus.MAX_ATTEMPTS_REACHED
                   or p.createdAt < :cutoff)
            group by p.tenant.id
            """)
    List<Object[]> countCriticosByTenant(@Param("cutoff") LocalDateTime cutoff);

    @Query("""
            select count(p)
            from Pagamento p
            where p.status = com.restaurante.financeiro.enums.StatusPagamentoGateway.PENDENTE
              and p.pollingStatus = com.restaurante.financeiro.enums.PagamentoPollingStatus.MAX_ATTEMPTS_REACHED
            """)
    long countMaxAttemptsReached();
}
