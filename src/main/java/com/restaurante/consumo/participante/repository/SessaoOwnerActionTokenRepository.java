package com.restaurante.consumo.participante.repository;

import com.restaurante.consumo.participante.entity.SessaoOwnerActionToken;
import com.restaurante.model.enums.SessaoOwnerActionTokenStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Prompt 41.4/41.5 — Repository para Owner Action Tokens.
 */
public interface SessaoOwnerActionTokenRepository extends JpaRepository<SessaoOwnerActionToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t
              from SessaoOwnerActionToken t
             where t.tokenHash = :tokenHash
               and t.tenant.id = :tenantId
            """)
    Optional<SessaoOwnerActionToken> findForUpdateByHash(@Param("tenantId") Long tenantId,
                                                         @Param("tokenHash") String tokenHash);

    Optional<SessaoOwnerActionToken> findByTokenHashAndTenant_Id(String tokenHash, Long tenantId);

    @Query("""
            select count(t) from SessaoOwnerActionToken t
             where t.tenant.id = :tenantId
               and t.ownerParticipante.id = :ownerParticipanteId
               and t.sessaoConsumo.id = :sessaoId
               and t.status = :status
               and t.expiresAt > :now
            """)
    long countActiveTokensForOwner(@Param("tenantId") Long tenantId,
                                   @Param("ownerParticipanteId") Long ownerParticipanteId,
                                   @Param("sessaoId") Long sessaoId,
                                   @Param("status") SessaoOwnerActionTokenStatus status,
                                   @Param("now") Instant now);

    // -------------------------------------------------------------------------
    // Prompt 41.5: revogação em massa ao fechar sessão
    // -------------------------------------------------------------------------

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t
              from SessaoOwnerActionToken t
             where t.tenant.id = :tenantId
               and t.sessaoConsumo.id = :sessaoId
               and t.status = com.restaurante.model.enums.SessaoOwnerActionTokenStatus.ACTIVE
            """)
    List<SessaoOwnerActionToken> findActiveTokensBySessaoForUpdate(@Param("tenantId") Long tenantId,
                                                                    @Param("sessaoId") Long sessaoId);

    // -------------------------------------------------------------------------
    // Prompt 41.5: cleanup de tokens finalizados antigos (busca IDs para batch delete)
    // -------------------------------------------------------------------------

    @Query("""
            select t.id
              from SessaoOwnerActionToken t
             where t.tenant.id = :tenantId
               and t.status in :statuses
               and t.createdAt < :cutoff
            """)
    List<Long> findFinalizedIdsBefore(@Param("tenantId") Long tenantId,
                                       @Param("statuses") Collection<SessaoOwnerActionTokenStatus> statuses,
                                       @Param("cutoff") Instant cutoff,
                                       Pageable pageable);

    @Query("""
            select t.id
              from SessaoOwnerActionToken t
             where t.status in :statuses
               and t.createdAt < :cutoff
            """)
    List<Long> findFinalizedIdsCrossTenantsBeforeForCleanup(
                                       @Param("statuses") Collection<SessaoOwnerActionTokenStatus> statuses,
                                       @Param("cutoff") Instant cutoff,
                                       Pageable pageable);

    @Modifying
    @Query("delete from SessaoOwnerActionToken t where t.id in :ids")
    int deleteByIds(@Param("ids") List<Long> ids);
}
