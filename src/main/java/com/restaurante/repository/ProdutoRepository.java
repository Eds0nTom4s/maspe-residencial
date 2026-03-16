package com.restaurante.repository;

import com.restaurante.model.entity.Produto;
import com.restaurante.model.enums.CategoriaProduto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

/**
 * Repository para operações de banco de dados com Produto
 */
@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    /**
     * Busca produto por código
     */
    Optional<Produto> findByCodigo(String codigo);

    /**
     * Verifica se existe produto com o código informado
     */
    boolean existsByCodigo(String codigo);

    /**
     * Busca produtos ativos com paginação
     */
    Page<Produto> findByAtivoTrue(Pageable pageable);

    /**
     * Busca produtos disponíveis com paginação
     */
    Page<Produto> findByDisponivelTrueAndAtivoTrue(Pageable pageable);

    /**
     * Busca produtos por categoria com paginação
     */
    Page<Produto> findByCategoriaAndDisponivelTrueAndAtivoTrue(CategoriaProduto categoria, Pageable pageable);

    /**
     * Busca produtos por nome (busca parcial) com paginação
     */
    Page<Produto> findByNomeContainingIgnoreCaseAndDisponivelTrueAndAtivoTrue(String nome, Pageable pageable);
}

