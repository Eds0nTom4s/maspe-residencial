package com.restaurante.repository;

import com.restaurante.model.entity.QrCodeOperacional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QrCodeOperacionalRepository extends JpaRepository<QrCodeOperacional, Long> {

    Optional<QrCodeOperacional> findByToken(String token);

    Optional<QrCodeOperacional> findByTokenAndAtivoTrueAndRevogadoFalse(String token);

    List<QrCodeOperacional> findByTenantId(Long tenantId);

    boolean existsByToken(String token);
}

