package com.restaurante.repository;

import com.restaurante.model.entity.ItemPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
