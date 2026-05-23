package com.restaurante.consumo.participante.repository;

import com.restaurante.consumo.participante.entity.SessaoConsumoParticipante;
import com.restaurante.model.enums.SessaoParticipanteRole;
import com.restaurante.model.enums.SessaoParticipanteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
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
}

