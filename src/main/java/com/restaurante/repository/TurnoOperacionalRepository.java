package com.restaurante.repository;

import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.enums.TurnoOperacionalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TurnoOperacionalRepository extends JpaRepository<TurnoOperacional, Long> {

    @Query("""
            select t
            from TurnoOperacional t
            where t.tenant.id = :tenantId
              and t.instituicao.id = :instituicaoId
              and t.unidadeAtendimento.id = :unidadeAtendimentoId
              and t.status in :statuses
            """)
    Optional<TurnoOperacional> findOpenByTenantAndInstituicaoAndUnidade(
            @Param("tenantId") Long tenantId,
            @Param("instituicaoId") Long instituicaoId,
            @Param("unidadeAtendimentoId") Long unidadeAtendimentoId,
            @Param("statuses") Iterable<TurnoOperacionalStatus> statuses
    );

    default Optional<TurnoOperacional> findOpenByTenantAndInstituicaoAndUnidade(Long tenantId, Long instituicaoId, Long unidadeAtendimentoId) {
        return findOpenByTenantAndInstituicaoAndUnidade(
                tenantId,
                instituicaoId,
                unidadeAtendimentoId,
                java.util.List.of(TurnoOperacionalStatus.ABERTO, TurnoOperacionalStatus.EM_FECHO)
        );
    }

    @Query("""
            select case when count(t) > 0 then true else false end
            from TurnoOperacional t
            where t.tenant.id = :tenantId
              and t.instituicao.id = :instituicaoId
              and t.unidadeAtendimento.id = :unidadeAtendimentoId
              and t.status in :statuses
            """)
    boolean existsOpenByTenantAndInstituicaoAndUnidade(
            @Param("tenantId") Long tenantId,
            @Param("instituicaoId") Long instituicaoId,
            @Param("unidadeAtendimentoId") Long unidadeAtendimentoId,
            @Param("statuses") Iterable<TurnoOperacionalStatus> statuses
    );

    default boolean existsOpenByTenantAndInstituicaoAndUnidade(Long tenantId, Long instituicaoId, Long unidadeAtendimentoId) {
        return existsOpenByTenantAndInstituicaoAndUnidade(
                tenantId,
                instituicaoId,
                unidadeAtendimentoId,
                java.util.List.of(TurnoOperacionalStatus.ABERTO, TurnoOperacionalStatus.EM_FECHO)
        );
    }

    Optional<TurnoOperacional> findByIdAndTenantId(Long id, Long tenantId);

    @Query("""
            select t
            from TurnoOperacional t
            where t.tenant.id = :tenantId
              and (:instituicaoId is null or t.instituicao.id = :instituicaoId)
              and (:unidadeAtendimentoId is null or t.unidadeAtendimento.id = :unidadeAtendimentoId)
              and (:status is null or t.status = :status)
              and (:de is null or t.abertoEm >= :de)
              and (:ate is null or t.abertoEm <= :ate)
            """)
    Page<TurnoOperacional> search(
            @Param("tenantId") Long tenantId,
            @Param("instituicaoId") Long instituicaoId,
            @Param("unidadeAtendimentoId") Long unidadeAtendimentoId,
            @Param("status") TurnoOperacionalStatus status,
            @Param("de") LocalDateTime de,
            @Param("ate") LocalDateTime ate,
            Pageable pageable
    );
}

