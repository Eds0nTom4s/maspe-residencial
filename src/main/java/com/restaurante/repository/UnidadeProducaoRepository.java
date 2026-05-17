package com.restaurante.repository;

import com.restaurante.model.entity.UnidadeProducao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface UnidadeProducaoRepository extends JpaRepository<UnidadeProducao, Long> {

    List<UnidadeProducao> findByTenantIdAndAtivoTrueOrderByOrdemAsc(Long tenantId);

    List<UnidadeProducao> findByTenantIdAndInstituicaoIdAndAtivoTrueOrderByOrdemAsc(Long tenantId, Long instituicaoId);

    Optional<UnidadeProducao> findByIdAndTenantId(Long id, Long tenantId);

    Optional<UnidadeProducao> findByTenantIdAndInstituicaoIdAndCodigo(Long tenantId, Long instituicaoId, String codigo);

    boolean existsByTenantIdAndInstituicaoIdAndCodigo(Long tenantId, Long instituicaoId, String codigo);

    long countByTenantId(Long tenantId);

    List<UnidadeProducao> findByTenantIdAndUnidadeAtendimentoIdAndAtivoTrueOrderByOrdemAsc(Long tenantId, Long unidadeAtendimentoId);

    List<UnidadeProducao> findByTenantIdAndAtivoTrueAndUpdatedAtAfterOrderByUpdatedAtAsc(Long tenantId, LocalDateTime updatedSince);
}
