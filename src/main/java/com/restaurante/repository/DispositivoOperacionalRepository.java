package com.restaurante.repository;

import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.enums.DispositivoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DispositivoOperacionalRepository extends JpaRepository<DispositivoOperacional, Long> {

    Optional<DispositivoOperacional> findByIdAndTenantId(Long id, Long tenantId);

    Optional<DispositivoOperacional> findByTenantIdAndCodigo(Long tenantId, String codigo);

    boolean existsByTenantIdAndCodigo(Long tenantId, String codigo);

    Optional<DispositivoOperacional> findByActivationCodeHash(String activationCodeHash);

    Optional<DispositivoOperacional> findByDeviceTokenHash(String deviceTokenHash);

    Page<DispositivoOperacional> findByTenantId(Long tenantId, Pageable pageable);

    long countByTenantIdAndStatusNot(Long tenantId, DispositivoStatus status);
}

