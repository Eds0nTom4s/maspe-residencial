package com.restaurante.repository;

import com.restaurante.model.entity.VariacaoProduto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório para VariacaoProduto.
 */
@Repository
public interface VariacaoProdutoRepository extends JpaRepository<VariacaoProduto, Long> {

    /**
     * Todas as variações activas de um produto.
     */
    List<VariacaoProduto> findByProdutoIdAndAtivoTrue(Long produtoId);

    /**
     * Todas as variações de um produto (incluindo inactivas).
     */
    List<VariacaoProduto> findByProdutoId(Long produtoId);

    Optional<VariacaoProduto> findByIdAndProdutoIdAndAtivoTrue(Long id, Long produtoId);

    Optional<VariacaoProduto> findBySkuAndAtivoTrue(String sku);
}
