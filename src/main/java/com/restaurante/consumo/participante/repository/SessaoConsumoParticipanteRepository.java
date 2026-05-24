package com.restaurante.consumo.participante.repository;

import com.restaurante.consumo.participante.entity.SessaoConsumoParticipante;
import com.restaurante.model.enums.SessaoParticipanteRole;
import com.restaurante.model.enums.SessaoParticipanteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface SessaoConsumoParticipanteRepository extends JpaRepository<SessaoConsumoParticipante, Long> {

    Optional<SessaoConsumoParticipante> findByTenant_IdAndId(Long tenantId, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
              from SessaoConsumoParticipante p
             where p.tenant.id = :tenantId
               and p.id = :id
            """)
    Optional<SessaoConsumoParticipante> findForUpdateById(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
              from SessaoConsumoParticipante p
             where p.tenant.id = :tenantId
               and p.sessaoConsumo.id = :sessaoId
               and p.clienteConsumo.id = :clienteConsumoId
            """)
    Optional<SessaoConsumoParticipante> findForUpdateBySessaoAndCliente(@Param("tenantId") Long tenantId,
                                                                        @Param("sessaoId") Long sessaoId,
                                                                        @Param("clienteConsumoId") Long clienteConsumoId);

    @Query("""
            select p
              from SessaoConsumoParticipante p
             where p.tenant.id = :tenantId
               and p.sessaoConsumo.id = :sessaoId
             order by p.joinedAt asc nulls last, p.id asc
            """)
    List<SessaoConsumoParticipante> listBySessao(@Param("tenantId") Long tenantId, @Param("sessaoId") Long sessaoId);

    @Query("""
            select p
              from SessaoConsumoParticipante p
             where p.tenant.id = :tenantId
               and p.sessaoConsumo.id = :sessaoId
               and p.status = :status
             order by p.joinedAt asc nulls last, p.id asc
            """)
    List<SessaoConsumoParticipante> listBySessaoAndStatus(@Param("tenantId") Long tenantId,
                                                          @Param("sessaoId") Long sessaoId,
                                                          @Param("status") SessaoParticipanteStatus status);

    Optional<SessaoConsumoParticipante> findByTenant_IdAndSessaoConsumo_IdAndClienteConsumo_Id(Long tenantId, Long sessaoId, Long clienteConsumoId);

    Optional<SessaoConsumoParticipante> findByTenant_IdAndSessaoConsumo_IdAndClienteConsumo_IdAndStatus(Long tenantId, Long sessaoId, Long clienteConsumoId, SessaoParticipanteStatus status);

    Optional<SessaoConsumoParticipante> findByTenant_IdAndSessaoConsumo_IdAndRole(Long tenantId, Long sessaoId, SessaoParticipanteRole role);

    Optional<SessaoConsumoParticipante> findByTenant_IdAndSessaoConsumo_IdAndTelefoneNormalizadoAndRoleAndStatus(Long tenantId,
                                                                                                                 Long sessaoId,
                                                                                                                 String telefoneNormalizado,
                                                                                                                 SessaoParticipanteRole role,
                                                                                                                 SessaoParticipanteStatus status);

    @Query("""
            select count(p)
              from SessaoConsumoParticipante p
             where p.tenant.id = :tenantId
               and p.sessaoConsumo.id = :sessaoId
               and p.role = com.restaurante.model.enums.SessaoParticipanteRole.OWNER
               and p.status = com.restaurante.model.enums.SessaoParticipanteStatus.ACTIVE
            """)
    long countActiveOwners(@Param("tenantId") Long tenantId, @Param("sessaoId") Long sessaoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
              from SessaoConsumoParticipante p
             where p.status in (com.restaurante.model.enums.SessaoParticipanteStatus.INVITED,
                                com.restaurante.model.enums.SessaoParticipanteStatus.PENDING_OTP,
                                com.restaurante.model.enums.SessaoParticipanteStatus.PENDING_APPROVAL)
               and p.expiresAt is not null
               and p.expiresAt < :now
               and p.expiredAt is null
               and p.cancelledAt is null
             order by p.expiresAt asc nulls last, p.id asc
            """)
    List<SessaoConsumoParticipante> findExpiredCandidatesForUpdate(@Param("now") java.time.Instant now, Pageable pageable);

    // -------------------------------------------------------------------------
    // Prompt 41.4 — Listagem paginada com filtros
    // -------------------------------------------------------------------------

    @Query("""
            select p from SessaoConsumoParticipante p
             where p.tenant.id = :tenantId
               and p.sessaoConsumo.id = :sessaoId
               and (:status is null or p.status = :status)
               and (:role is null or p.role = :role)
             order by p.joinedAt asc nulls last, p.id asc
            """)
    org.springframework.data.domain.Page<SessaoConsumoParticipante> listBySessaoPaged(
            @Param("tenantId") Long tenantId,
            @Param("sessaoId") Long sessaoId,
            @Param("status") SessaoParticipanteStatus status,
            @Param("role") SessaoParticipanteRole role,
            Pageable pageable);

    @Query("""
            select p from SessaoConsumoParticipante p
             where p.tenant.id = :tenantId
               and p.sessaoConsumo.id = :sessaoId
               and p.status in :statuses
             order by p.createdAt desc nulls last, p.id asc
            """)
    org.springframework.data.domain.Page<SessaoConsumoParticipante> listBySessaoAndStatuses(
            @Param("tenantId") Long tenantId,
            @Param("sessaoId") Long sessaoId,
            @Param("statuses") java.util.Collection<SessaoParticipanteStatus> statuses,
            Pageable pageable);

    @Query("""
            select p from SessaoConsumoParticipante p
             where p.tenant.id = :tenantId
               and p.sessaoConsumo.id = :sessaoId
               and p.invitedByParticipanteId = :ownerParticipanteId
             order by p.invitedAt desc nulls last, p.id asc
            """)
    org.springframework.data.domain.Page<SessaoConsumoParticipante> listOwnerSentInvites(
            @Param("tenantId") Long tenantId,
            @Param("sessaoId") Long sessaoId,
            @Param("ownerParticipanteId") Long ownerParticipanteId,
            Pageable pageable);
}
