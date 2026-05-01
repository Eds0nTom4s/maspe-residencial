package com.restaurante.repository;

import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.enums.StatusSubPedido;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository para SubPedido
 */
@Repository
public interface SubPedidoRepository extends JpaRepository<SubPedido, Long> {

    /**
     * Busca SubPedido por número
     */
    Optional<SubPedido> findByNumero(String numero);

    /**
     * Busca SubPedido por ID com todos os relacionamentos carregados (para evitar LazyInitializationException)
     */
    @Query("SELECT DISTINCT sp FROM SubPedido sp " +
           "LEFT JOIN FETCH sp.pedido " +
           "LEFT JOIN FETCH sp.cozinha " +
           "LEFT JOIN FETCH sp.unidadeAtendimento " +
           "LEFT JOIN FETCH sp.itens i " +
           "LEFT JOIN FETCH i.produto " +
           "LEFT JOIN FETCH i.variacaoProduto " +
           "WHERE sp.id = :id")
    Optional<SubPedido> findByIdWithDetails(@Param("id") Long id);

    /**
     * Busca SubPedido por ID com lock pessimista (para operações concorrentes)
     * Força lock de linha no banco para prevenir race conditions
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sp FROM SubPedido sp WHERE sp.id = :id")
    Optional<SubPedido> findByIdWithLock(@Param("id") Long id);

    /**
     * Busca SubPedidos de um pedido (com itens e produtos carregados)
     */
    @Query("SELECT DISTINCT sp FROM SubPedido sp " +
           "LEFT JOIN FETCH sp.pedido " +
           "LEFT JOIN FETCH sp.cozinha " +
           "LEFT JOIN FETCH sp.unidadeAtendimento " +
           "LEFT JOIN FETCH sp.itens i " +
           "LEFT JOIN FETCH i.produto " +
           "WHERE sp.pedido.id = :pedidoId " +
           "ORDER BY sp.createdAt ASC")
    List<SubPedido> findByPedidoIdOrderByCreatedAtAsc(@Param("pedidoId") Long pedidoId);

    /**
     * Busca SubPedidos por status (com itens e produtos carregados)
     */
    @Query("SELECT DISTINCT sp FROM SubPedido sp " +
           "LEFT JOIN FETCH sp.pedido " +
           "LEFT JOIN FETCH sp.cozinha " +
           "LEFT JOIN FETCH sp.unidadeAtendimento " +
           "LEFT JOIN FETCH sp.itens i " +
           "LEFT JOIN FETCH i.produto " +
           "WHERE sp.status = :status " +
           "ORDER BY sp.createdAt ASC")
    List<SubPedido> findByStatusOrderByCreatedAtAsc(@Param("status") StatusSubPedido status);

    /**
     * Busca SubPedidos por múltiplos status (com itens e produtos carregados)
     */
    @Query("SELECT DISTINCT sp FROM SubPedido sp " +
           "LEFT JOIN FETCH sp.pedido " +
           "LEFT JOIN FETCH sp.cozinha " +
           "LEFT JOIN FETCH sp.unidadeAtendimento " +
           "LEFT JOIN FETCH sp.itens i " +
           "LEFT JOIN FETCH i.produto " +
           "LEFT JOIN FETCH i.variacaoProduto " +
           "WHERE sp.status IN :status " +
           "ORDER BY sp.createdAt ASC")
    List<SubPedido> findByStatusInOrderByCreatedAtAsc(@Param("status") List<StatusSubPedido> status);

    /**
     * Busca SubPedidos de uma cozinha por status (com itens e produtos carregados)
     */
    @Query("SELECT DISTINCT sp FROM SubPedido sp " +
           "LEFT JOIN FETCH sp.pedido " +
           "LEFT JOIN FETCH sp.cozinha " +
           "LEFT JOIN FETCH sp.unidadeAtendimento " +
           "LEFT JOIN FETCH sp.itens i " +
           "LEFT JOIN FETCH i.produto " +
           "WHERE sp.cozinha.id = :cozinhaId AND sp.status = :status " +
           "ORDER BY sp.createdAt ASC")
    List<SubPedido> findByCozinhaIdAndStatusOrderByCreatedAtAsc(
            @Param("cozinhaId") Long cozinhaId, 
            @Param("status") StatusSubPedido status);

    /**
     * Busca SubPedidos ativos de uma cozinha (com itens e produtos carregados)
     */
    @Query("SELECT DISTINCT sp FROM SubPedido sp " +
           "LEFT JOIN FETCH sp.pedido " +
           "LEFT JOIN FETCH sp.cozinha " +
           "LEFT JOIN FETCH sp.unidadeAtendimento " +
           "LEFT JOIN FETCH sp.itens i " +
           "LEFT JOIN FETCH i.produto " +
           "WHERE sp.cozinha.id = :cozinhaId " +
           "AND sp.status IN ('PENDENTE', 'RECEBIDO', 'EM_PREPARACAO', 'PRONTO') " +
           "ORDER BY sp.createdAt ASC")
    List<SubPedido> findSubPedidosAtivosByCozinha(@Param("cozinhaId") Long cozinhaId);

    /**
     * Busca SubPedidos de uma unidade de atendimento (com itens e produtos carregados)
     */
    @Query("SELECT DISTINCT sp FROM SubPedido sp " +
           "LEFT JOIN FETCH sp.pedido " +
           "LEFT JOIN FETCH sp.cozinha " +
           "LEFT JOIN FETCH sp.unidadeAtendimento " +
           "LEFT JOIN FETCH sp.itens i " +
           "LEFT JOIN FETCH i.produto " +
           "WHERE sp.unidadeAtendimento.id = :unidadeId " +
           "ORDER BY sp.createdAt DESC")
    List<SubPedido> findByUnidadeAtendimentoIdOrderByCreatedAtDesc(@Param("unidadeId") Long unidadeId);

    /**
     * Busca SubPedidos por responsável de preparo (com itens e produtos carregados)
     */
    @Query("SELECT DISTINCT sp FROM SubPedido sp " +
           "LEFT JOIN FETCH sp.pedido " +
           "LEFT JOIN FETCH sp.cozinha " +
           "LEFT JOIN FETCH sp.unidadeAtendimento " +
           "LEFT JOIN FETCH sp.itens i " +
           "LEFT JOIN FETCH i.produto " +
           "WHERE sp.responsavelPreparo = :responsavel " +
           "ORDER BY sp.createdAt DESC")
    List<SubPedido> findByResponsavelPreparoOrderByCreatedAtDesc(@Param("responsavel") String responsavel);

    /**
     * Busca SubPedidos prontos aguardando entrega (com itens e produtos carregados)
     */
    @Query("SELECT DISTINCT sp FROM SubPedido sp " +
           "LEFT JOIN FETCH sp.pedido " +
           "LEFT JOIN FETCH sp.cozinha " +
           "LEFT JOIN FETCH sp.unidadeAtendimento " +
           "LEFT JOIN FETCH sp.itens i " +
           "LEFT JOIN FETCH i.produto " +
           "WHERE sp.status = 'PRONTO' " +
           "ORDER BY sp.prontoEm ASC")
    List<SubPedido> findSubPedidosProntosParaEntrega();

    /**
     * Busca SubPedidos com tempo de espera excessivo (com itens e produtos carregados)
     * (mais de X minutos no mesmo status)
     */
    @Query("SELECT DISTINCT sp FROM SubPedido sp " +
           "LEFT JOIN FETCH sp.pedido " +
           "LEFT JOIN FETCH sp.cozinha " +
           "LEFT JOIN FETCH sp.unidadeAtendimento " +
           "LEFT JOIN FETCH sp.itens i " +
           "LEFT JOIN FETCH i.produto " +
           "WHERE sp.status IN ('PENDENTE', 'RECEBIDO', 'EM_PREPARACAO') " +
           "AND sp.createdAt < :tempo " +
           "ORDER BY sp.createdAt ASC")
    List<SubPedido> findSubPedidosComAtraso(@Param("tempo") LocalDateTime tempo);

    /**
     * Conta SubPedidos ativos por cozinha
     */
    @Query("SELECT COUNT(sp) FROM SubPedido sp WHERE sp.cozinha.id = :cozinhaId " +
           "AND sp.status IN ('PENDENTE', 'RECEBIDO', 'EM_PREPARACAO')")
    Long contarSubPedidosAtivosPorCozinha(@Param("cozinhaId") Long cozinhaId);
}
