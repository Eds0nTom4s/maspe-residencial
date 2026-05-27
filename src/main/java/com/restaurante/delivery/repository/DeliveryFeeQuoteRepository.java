package com.restaurante.delivery.repository;

import com.restaurante.model.entity.DeliveryFeeQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeliveryFeeQuoteRepository extends JpaRepository<DeliveryFeeQuote, Long> {
    Optional<DeliveryFeeQuote> findByPedidoId(Long pedidoId);
}
