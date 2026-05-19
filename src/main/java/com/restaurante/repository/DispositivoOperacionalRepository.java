package com.restaurante.repository;

import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.enums.DispositivoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface DispositivoOperacionalRepository extends JpaRepository<DispositivoOperacional, Long> {

    Optional<DispositivoOperacional> findByIdAndTenantId(Long id, Long tenantId);

    Optional<DispositivoOperacional> findByTenantIdAndCodigo(Long tenantId, String codigo);

    boolean existsByTenantIdAndCodigo(Long tenantId, String codigo);

    Optional<DispositivoOperacional> findByActivationCodeHash(String activationCodeHash);

    Optional<DispositivoOperacional> findByDeviceTokenHash(String deviceTokenHash);

    Page<DispositivoOperacional> findByTenantId(Long tenantId, Pageable pageable);

    long countByTenantIdAndStatusNot(Long tenantId, DispositivoStatus status);

    @Query("""
            select count(d)
            from DispositivoOperacional d
            where d.tenant.id = :tenantId
              and d.unidadeAtendimento.id = :unidadeAtendimentoId
              and d.status = com.restaurante.model.enums.DispositivoStatus.ATIVO
              and (d.ultimoHeartbeatEm is null or d.ultimoHeartbeatEm < :cutoff)
            """)
    long countOfflineByTenantAndUnidadeAtendimento(@Param("tenantId") Long tenantId,
                                                  @Param("unidadeAtendimentoId") Long unidadeAtendimentoId,
                                                  @Param("cutoff") LocalDateTime cutoff);
}
