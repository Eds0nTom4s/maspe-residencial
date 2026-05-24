package com.restaurante.financeiro.caixa.repository;

import com.restaurante.model.entity.CaixaOperadorSession;
import com.restaurante.model.enums.CaixaOperadorSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface CaixaOperadorSessionRepository extends JpaRepository<CaixaOperadorSession, Long> {

    Optional<CaixaOperadorSession> findByTenantIdAndDispositivoOperacionalIdAndStatus(Long tenantId, Long dispositivoOperacionalId, CaixaOperadorSessionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CaixaOperadorSession c where c.id = :id")
    Optional<CaixaOperadorSession> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select c
            from CaixaOperadorSession c
            where c.tenant.id = :tenantId
              and (:status is null or c.status = :status)
              and (:unidadeId is null or c.unidadeAtendimento.id = :unidadeId)
              and (:turnoId is null or c.turnoOperacional.id = :turnoId)
              and (:deviceId is null or c.dispositivoOperacional.id = :deviceId)
              and (:operadorUserId is null or c.operador.id = :operadorUserId)
            order by c.openedAt desc
            """)
    Page<CaixaOperadorSession> searchByTenantAndFilters(@Param("tenantId") Long tenantId,
                                                       @Param("status") CaixaOperadorSessionStatus status,
                                                       @Param("unidadeId") Long unidadeId,
                                                       @Param("turnoId") Long turnoId,
                                                       @Param("deviceId") Long deviceId,
                                                       @Param("operadorUserId") Long operadorUserId,
                                                       Pageable pageable);
}
