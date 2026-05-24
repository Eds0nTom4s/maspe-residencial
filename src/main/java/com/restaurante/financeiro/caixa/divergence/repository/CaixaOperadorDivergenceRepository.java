package com.restaurante.financeiro.caixa.divergence.repository;

import com.restaurante.model.entity.CaixaOperadorDivergence;
import com.restaurante.model.enums.CaixaOperadorDivergenceStatus;
import com.restaurante.model.enums.CaixaOperadorDivergenceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface CaixaOperadorDivergenceRepository extends JpaRepository<CaixaOperadorDivergence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from CaixaOperadorDivergence d where d.id = :id")
    Optional<CaixaOperadorDivergence> findByIdForUpdate(@Param("id") Long id);

    List<CaixaOperadorDivergence> findByTenantIdAndCaixaOperadorSessionId(Long tenantId, Long caixaId);

    @Query("""
            select d
            from CaixaOperadorDivergence d
            where d.tenant.id = :tenantId
              and d.turnoOperacional.id = :turnoId
            order by d.createdAt asc
            """)
    List<CaixaOperadorDivergence> findAllByTenantIdAndTurnoOperacionalId(@Param("tenantId") Long tenantId,
                                                                         @Param("turnoId") Long turnoId);

    @Query("""
            select d
            from CaixaOperadorDivergence d
            where d.tenant.id = :tenantId
              and (:status is null or d.status = :status)
            order by d.createdAt desc
            """)
    Page<CaixaOperadorDivergence> searchByTenant(@Param("tenantId") Long tenantId,
                                                 @Param("status") CaixaOperadorDivergenceStatus status,
                                                 Pageable pageable);

    @Query("""
            select count(d)
            from CaixaOperadorDivergence d
            where d.tenant.id = :tenantId
              and d.caixaOperadorSession.id = :caixaId
              and d.status in ('DRAFT','SUBMITTED')
            """)
    long countOpenByTenantAndCaixa(@Param("tenantId") Long tenantId, @Param("caixaId") Long caixaId);

    @Query("""
            select d
            from CaixaOperadorDivergence d
            where d.tenant.id = :tenantId
              and d.caixaOperadorSession.id = :caixaId
              and d.type = :type
              and d.status in ('DRAFT','SUBMITTED')
            """)
    Optional<CaixaOperadorDivergence> findOpenByTenantAndCaixaAndType(@Param("tenantId") Long tenantId,
                                                                      @Param("caixaId") Long caixaId,
                                                                      @Param("type") CaixaOperadorDivergenceType type);
}

