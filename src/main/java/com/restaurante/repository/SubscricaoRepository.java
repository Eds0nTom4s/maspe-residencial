package com.restaurante.repository;

import com.restaurante.model.entity.Subscricao;
import com.restaurante.model.enums.SubscricaoEstado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscricaoRepository extends JpaRepository<Subscricao, Long> {

    Optional<Subscricao> findByTenantIdAndEstado(Long tenantId, SubscricaoEstado estado);

    List<Subscricao> findByTenantId(Long tenantId);
}

