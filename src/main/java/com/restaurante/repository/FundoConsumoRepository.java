package com.restaurante.repository;

import com.restaurante.model.entity.FundoConsumo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FundoConsumoRepository extends JpaRepository<FundoConsumo, Long> {

    /**
     * Busca fundo ativo por cliente
     */
    @Query("SELECT f FROM FundoConsumo f WHERE f.cliente.id = :clienteId AND f.ativo = true")
    Optional<FundoConsumo> findByClienteIdAndAtivoTrue(Long clienteId);

    /**
     * Verifica se cliente já tem fundo ativo
     */
    boolean existsByClienteIdAndAtivoTrue(Long clienteId);
}
