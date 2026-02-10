package com.restaurante.repository;

import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.enums.StatusPagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository para operações de banco de dados com Pagamento
 */
@Repository
public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {

    /**
     * Busca pagamento por unidade de consumo
     */
    Optional<Pagamento> findByUnidadeConsumoId(Long unidadeConsumoId);

    /**
     * Busca pagamento por ID de transação
     */
    Optional<Pagamento> findByTransactionId(String transactionId);

    /**
     * Busca pagamentos por status
     */
    List<Pagamento> findByStatus(StatusPagamento status);

    /**
     * Verifica se existe pagamento para uma unidade de consumo
     */
    boolean existsByUnidadeConsumoId(Long unidadeConsumoId);
}
