package com.restaurante.repository;

import com.restaurante.model.entity.RotaProducaoCategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RotaProducaoCategoriaRepository extends JpaRepository<RotaProducaoCategoria, Long> {

    Optional<RotaProducaoCategoria> findByTenantIdAndCategoriaProdutoIdAndAtivoTrue(Long tenantId, Long categoriaProdutoId);

    List<RotaProducaoCategoria> findByTenantId(Long tenantId);

    Optional<RotaProducaoCategoria> findByIdAndTenantId(Long id, Long tenantId);
}

