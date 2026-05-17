package com.restaurante.repository;

import com.restaurante.model.entity.CategoriaProduto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface CategoriaProdutoRepository extends JpaRepository<CategoriaProduto, Long> {

    Optional<CategoriaProduto> findByIdAndTenantId(Long id, Long tenantId);

    Optional<CategoriaProduto> findBySlugAndTenantId(String slug, Long tenantId);

    boolean existsBySlugAndTenantId(String slug, Long tenantId);

    List<CategoriaProduto> findByTenantId(Long tenantId);

    List<CategoriaProduto> findByTenantIdAndAtivoTrueOrderByOrdemAsc(Long tenantId);

    List<CategoriaProduto> findByTenantIdAndUpdatedAtAfterOrderByUpdatedAtAsc(Long tenantId, LocalDateTime updatedSince);
}
