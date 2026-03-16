package com.restaurante.repository;

import com.restaurante.model.entity.ItemPedido;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository para operações de banco de dados com ItemPedido
 */
@Repository
public interface ItemPedidoRepository extends JpaRepository<ItemPedido, Long> {

    /**
     * Busca itens de um pedido específico
     */
    List<ItemPedido> findByPedidoId(Long pedidoId);

    /**
     * Busca itens por produto
     */
    List<ItemPedido> findByProdutoId(Long produtoId);

    /**
     * Conta itens de um pedido
     */
    long countByPedidoId(Long pedidoId);

    /**
     * Retorna os produtos mais vendidos num período, ordenados por quantidade vendida descendente.
     * Retorna List<Object[]> com: [produtoId, produtoNome, categoriaStr, somaQuantidade, somaValor]
     */
    @Query("SELECT i.produto.id, i.produto.nome, CAST(i.produto.categoria AS string), " +
           "SUM(i.quantidade), SUM(i.subtotal) " +
           "FROM ItemPedido i " +
           "WHERE i.pedido.createdAt >= :dataInicio " +
           "GROUP BY i.produto.id, i.produto.nome, i.produto.categoria " +
           "ORDER BY SUM(i.quantidade) DESC")
    List<Object[]> findTopProdutosPorQuantidade(
            @Param("dataInicio") LocalDateTime dataInicio,
            Pageable pageable);

    /**
     * Retorna os top-produtos de um período específico (inicio e fim).
     */
    @Query("SELECT i.produto.id, i.produto.nome, CAST(i.produto.categoria AS string), " +
           "SUM(i.quantidade), SUM(i.subtotal) " +
           "FROM ItemPedido i " +
           "WHERE i.pedido.createdAt BETWEEN :dataInicio AND :dataFim " +
           "GROUP BY i.produto.id, i.produto.nome, i.produto.categoria " +
           "ORDER BY SUM(i.quantidade) DESC")
    List<Object[]> findTopProdutosPorPeriodo(
            @Param("dataInicio") LocalDateTime dataInicio,
            @Param("dataFim") LocalDateTime dataFim,
            Pageable pageable);
}
