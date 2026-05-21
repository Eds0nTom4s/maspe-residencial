package com.restaurante.repository;

import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.model.enums.OperationalDeviceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
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

    @Query("""
            select count(d)
            from DispositivoOperacional d
            where d.status = com.restaurante.model.enums.DispositivoStatus.ATIVO
              and (d.ultimoHeartbeatEm is null or d.ultimoHeartbeatEm < :cutoff)
            """)
    long countOfflineGlobal(@Param("cutoff") LocalDateTime cutoff);

    long countByStatus(DispositivoStatus status);

    @Query("""
            select d
            from DispositivoOperacional d
            where d.tenant.id = :tenantId
              and (:status is null or d.status = :status)
              and (:tipo is null or d.tipo = :tipo)
              and (:unidadeAtendimentoId is null or d.unidadeAtendimento.id = :unidadeAtendimentoId)
              and (:unidadeProducaoId is null or d.unidadeProducao.id = :unidadeProducaoId)
            order by d.id desc
            """)
    Page<DispositivoOperacional> searchByTenantAndFilters(
            @Param("tenantId") Long tenantId,
            @Param("status") DispositivoStatus status,
            @Param("tipo") DispositivoTipo tipo,
            @Param("unidadeAtendimentoId") Long unidadeAtendimentoId,
            @Param("unidadeProducaoId") Long unidadeProducaoId,
            Pageable pageable
    );

    @Query("""
            select d
            from DispositivoOperacional d
            where d.tenant.id = :tenantId
              and d.unidadeAtendimento.id = :unidadeAtendimentoId
              and (:deviceType is null or d.operationalDeviceType = :deviceType)
            order by d.id asc
            """)
    List<DispositivoOperacional> findByTenantAndUnidadeAtendimentoAndOperationalType(
            @Param("tenantId") Long tenantId,
            @Param("unidadeAtendimentoId") Long unidadeAtendimentoId,
            @Param("deviceType") OperationalDeviceType deviceType
    );

    @Query("""
            select d
            from DispositivoOperacional d
            where d.tenant.id = :tenantId
              and d.unidadeAtendimento.id = :unidadeAtendimentoId
              and d.id in :ids
            order by d.id asc
            """)
    List<DispositivoOperacional> findByTenantAndUnidadeAtendimentoAndIds(
            @Param("tenantId") Long tenantId,
            @Param("unidadeAtendimentoId") Long unidadeAtendimentoId,
            @Param("ids") Collection<Long> ids
    );

    @Query("""
            select d.tenant.id as tenantId, count(d) as cnt
            from DispositivoOperacional d
            where d.status = com.restaurante.model.enums.DispositivoStatus.ATIVO
            group by d.tenant.id
            """)
    java.util.List<Object[]> countAtivosByTenant();

    @Query("""
            select d.tenant.id as tenantId, count(d) as cnt
            from DispositivoOperacional d
            where d.status = com.restaurante.model.enums.DispositivoStatus.ATIVO
              and (d.ultimoHeartbeatEm is null or d.ultimoHeartbeatEm < :cutoff)
            group by d.tenant.id
            """)
    java.util.List<Object[]> countOfflineByTenant(@Param("cutoff") LocalDateTime cutoff);
}
