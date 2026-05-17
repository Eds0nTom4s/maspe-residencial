package com.restaurante.repository;

import com.restaurante.model.entity.QrCodeOperacional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface QrCodeOperacionalRepository extends JpaRepository<QrCodeOperacional, Long> {

    Optional<QrCodeOperacional> findByToken(String token);

    Optional<QrCodeOperacional> findByTokenAndAtivoTrueAndRevogadoFalse(String token);

    List<QrCodeOperacional> findByTenantId(Long tenantId);

    Optional<QrCodeOperacional> findByIdAndTenantId(Long id, Long tenantId);

    Optional<QrCodeOperacional> findFirstByMesaIdAndTenantIdAndAtivoTrueAndRevogadoFalse(Long mesaId, Long tenantId);

    List<QrCodeOperacional> findByTenantIdAndAtivoTrueAndRevogadoFalse(Long tenantId);

    List<QrCodeOperacional> findByTenantIdAndUnidadeAtendimentoIdAndAtivoTrueAndRevogadoFalse(Long tenantId, Long unidadeAtendimentoId);

    List<QrCodeOperacional> findByTenantIdAndAtivoTrueAndRevogadoFalseAndUpdatedAtAfter(Long tenantId, LocalDateTime updatedSince);

    List<QrCodeOperacional> findByTenantIdAndUnidadeAtendimentoIdAndAtivoTrueAndRevogadoFalseAndUpdatedAtAfter(Long tenantId, Long unidadeAtendimentoId, LocalDateTime updatedSince);

    boolean existsByToken(String token);

    long countByTenantId(Long tenantId);
}
