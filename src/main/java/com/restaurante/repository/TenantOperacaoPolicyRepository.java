package com.restaurante.repository;

import com.restaurante.model.entity.TenantOperacaoPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantOperacaoPolicyRepository extends JpaRepository<TenantOperacaoPolicy, Long> {
    Optional<TenantOperacaoPolicy> findByTenantId(Long tenantId);
}

