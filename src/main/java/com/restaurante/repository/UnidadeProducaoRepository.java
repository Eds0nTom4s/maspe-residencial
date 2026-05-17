package com.restaurante.repository;

import com.restaurante.model.entity.UnidadeProducao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnidadeProducaoRepository extends JpaRepository<UnidadeProducao, Long> {

    List<UnidadeProducao> findByTenantIdAndAtivoTrueOrderByOrdemAsc(Long tenantId);

    Optional<UnidadeProducao> findByIdAndTenantId(Long id, Long tenantId);

    Optional<UnidadeProducao> findByTenantIdAndInstituicaoIdAndCodigo(Long tenantId, Long instituicaoId, String codigo);

    boolean existsByTenantIdAndInstituicaoIdAndCodigo(Long tenantId, Long instituicaoId, String codigo);

    long countByTenantId(Long tenantId);
}

