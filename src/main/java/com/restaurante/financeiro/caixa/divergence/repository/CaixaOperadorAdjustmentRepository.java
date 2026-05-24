package com.restaurante.financeiro.caixa.divergence.repository;

import com.restaurante.model.entity.CaixaOperadorAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CaixaOperadorAdjustmentRepository extends JpaRepository<CaixaOperadorAdjustment, Long> {

    @Query("""
            select a
            from CaixaOperadorAdjustment a
            where a.tenant.id = :tenantId
              and a.divergence.turnoOperacional.id = :turnoId
            order by a.createdAt asc
            """)
    List<CaixaOperadorAdjustment> findAllByTenantIdAndTurnoOperacionalId(@Param("tenantId") Long tenantId,
                                                                         @Param("turnoId") Long turnoId);

    @Query("""
            select a
            from CaixaOperadorAdjustment a
            where a.tenant.id = :tenantId
            order by a.createdAt desc
            """)
    Page<CaixaOperadorAdjustment> findByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);
}

