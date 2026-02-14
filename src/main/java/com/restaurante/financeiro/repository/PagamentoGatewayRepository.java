package com.restaurante.financeiro.repository;

import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.enums.TipoPagamentoFinanceiro;
import com.restaurante.model.entity.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
    
    /**
     * Busca último pagamento confirmado do fundo
     */
    @Query("SELECT p FROM Pagamento p WHERE p.fundoConsumo.id = :fundoId " +
           "AND p.status = 'CONFIRMADO' ORDER BY p.confirmedAt DESC")
    List<Pagamento> findUltimosPagamentosConfirmadosFundo(Long fundoId);
}
