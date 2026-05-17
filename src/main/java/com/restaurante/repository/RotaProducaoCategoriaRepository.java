package com.restaurante.repository;

import com.restaurante.model.entity.RotaProducaoCategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface RotaProducaoCategoriaRepository extends JpaRepository<RotaProducaoCategoria, Long> {

    Optional<RotaProducaoCategoria> findByTenantIdAndCategoriaProdutoIdAndAtivoTrue(Long tenantId, Long categoriaProdutoId);

    List<RotaProducaoCategoria> findByTenantId(Long tenantId);

    Optional<RotaProducaoCategoria> findByIdAndTenantId(Long id, Long tenantId);

    List<RotaProducaoCategoria> findByTenantIdAndAtivoTrue(Long tenantId);

    List<RotaProducaoCategoria> findByTenantIdAndUpdatedAtAfterOrderByUpdatedAtAsc(Long tenantId, LocalDateTime updatedSince);
}
