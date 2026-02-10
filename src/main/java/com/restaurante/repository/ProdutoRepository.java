package com.restaurante.repository;

import com.restaurante.model.entity.Produto;
import com.restaurante.model.enums.CategoriaProduto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
     * Busca produtos ativos
     */
    List<Produto> findByAtivoTrue();

    /**
     * Busca produtos disponíveis
     */
    List<Produto> findByDisponivelTrueAndAtivoTrue();

    /**
     * Busca produtos por categoria
     */
    List<Produto> findByCategoriaAndDisponivelTrueAndAtivoTrue(CategoriaProduto categoria);

    /**
     * Busca produtos por nome (busca parcial)
     */
    List<Produto> findByNomeContainingIgnoreCaseAndDisponivelTrueAndAtivoTrue(String nome);
}
