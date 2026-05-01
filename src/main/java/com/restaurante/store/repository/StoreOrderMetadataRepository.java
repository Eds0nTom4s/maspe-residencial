package com.restaurante.store.repository;

import com.restaurante.store.model.StoreOrderMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface StoreOrderMetadataRepository extends JpaRepository<StoreOrderMetadata, Long> {
    Optional<StoreOrderMetadata> findByPedidoId(Long pedidoId);
    Optional<StoreOrderMetadata> findByPedidoNumero(String numero);
    Optional<StoreOrderMetadata> findByIdempotencyKeyAndSocioId(String idempotencyKey, String socioId);
    boolean existsByPedidoId(Long pedidoId);
    List<StoreOrderMetadata> findBySocioIdOrderByCreatedAtDesc(String socioId);
    Page<StoreOrderMetadata> findAllByOrderByCreatedAtDesc(Pageable pageable);
    long countByPedidoStatusFinanceiro(com.restaurante.model.enums.StatusFinanceiroPedido statusFinanceiro);
    long countByPedidoStatus(com.restaurante.model.enums.StatusPedido status);

    @Query("SELECT COALESCE(SUM(m.pedido.total), 0) FROM StoreOrderMetadata m " +
           "WHERE m.pedido.statusFinanceiro = 'PAGO'")
    BigDecimal sumReceitaPaga();
}
