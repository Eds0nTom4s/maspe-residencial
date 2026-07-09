package com.restaurante.consumo.identificacao.repository;

import com.restaurante.consumo.identificacao.entity.ClienteConsumo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClienteConsumoRepository extends JpaRepository<ClienteConsumo, Long> {
    Optional<ClienteConsumo> findByIdAndTenant_Id(Long id, Long tenantId);
    Optional<ClienteConsumo> findByTenant_IdAndTelefoneNormalizado(Long tenantId, String telefoneNormalizado);
}

