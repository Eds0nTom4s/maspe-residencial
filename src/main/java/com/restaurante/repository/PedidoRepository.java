package com.restaurante.repository;

import com.restaurante.model.entity.Pedido;
import com.restaurante.model.enums.StatusPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository para operações de banco de dados com Pedido
 */
@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    /**
     * Busca pedido por número
     */
    Optional<Pedido> findByNumero(String numero);

    /**
     * Busca pedidos de uma mesa
     */
    List<Pedido> findByMesaIdOrderByCreatedAtDesc(Long mesaId);

    /**
     * Busca pedidos por status
     */
    List<Pedido> findByStatusOrderByCreatedAtAsc(StatusPedido status);

    /**
     * Busca pedidos pendentes e recebidos (para atendente)
     */
    List<Pedido> findByStatusInOrderByCreatedAtAsc(List<StatusPedido> status);

    /**
     * Conta pedidos de uma mesa
     */
    long countByMesaId(Long mesaId);

    /**
     * Busca último pedido de uma mesa
     */
    Optional<Pedido> findTopByMesaIdOrderByCreatedAtDesc(Long mesaId);

    /**
     * Busca pedidos de hoje por status
     */
    @Query("SELECT p FROM Pedido p WHERE CAST(p.createdAt AS date) = CURRENT_DATE AND p.status = :status ORDER BY p.createdAt ASC")
    List<Pedido> findPedidosDeHojePorStatus(StatusPedido status);
}
