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
     * Busca SubPedido por ID com lock pessimista (para operações concorrentes)
     * Força lock de linha no banco para prevenir race conditions
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sp FROM SubPedido sp WHERE sp.id = :id")
    Optional<SubPedido> findByIdWithLock(@Param("id") Long id);

    /**
     * Busca SubPedidos de um pedido
     */
    List<SubPedido> findByPedidoIdOrderByCreatedAtAsc(Long pedidoId);

    /**
     * Busca SubPedidos por status
     */
    List<SubPedido> findByStatusOrderByCreatedAtAsc(StatusSubPedido status);

    /**
     * Busca SubPedidos por múltiplos status
     */
    List<SubPedido> findByStatusInOrderByCreatedAtAsc(List<StatusSubPedido> status);

    /**
     * Busca SubPedidos de uma cozinha por status
     */
    List<SubPedido> findByCozinhaIdAndStatusOrderByCreatedAtAsc(Long cozinhaId, StatusSubPedido status);

    /**
     * Busca SubPedidos ativos de uma cozinha
     */
    @Query("SELECT sp FROM SubPedido sp WHERE sp.cozinha.id = :cozinhaId " +
           "AND sp.status IN ('PENDENTE', 'RECEBIDO', 'EM_PREPARACAO', 'PRONTO') " +
           "ORDER BY sp.createdAt ASC")
    List<SubPedido> findSubPedidosAtivosByCozinha(@Param("cozinhaId") Long cozinhaId);

    /**
     * Busca SubPedidos de uma unidade de atendimento
     */
    List<SubPedido> findByUnidadeAtendimentoIdOrderByCreatedAtDesc(Long unidadeId);

    /**
     * Busca SubPedidos por responsável de preparo
     */
    List<SubPedido> findByResponsavelPreparoOrderByCreatedAtDesc(String responsavel);

    /**
     * Busca SubPedidos prontos aguardando entrega
     */
    @Query("SELECT sp FROM SubPedido sp WHERE sp.status = 'PRONTO' " +
           "ORDER BY sp.prontoEm ASC")
    List<SubPedido> findSubPedidosProntosParaEntrega();

    /**
     * Busca SubPedidos com tempo de espera excessivo
     * (mais de X minutos no mesmo status)
     */
    @Query("SELECT sp FROM SubPedido sp WHERE sp.status IN ('PENDENTE', 'RECEBIDO', 'EM_PREPARACAO') " +
           "AND sp.createdAt < :tempo ORDER BY sp.createdAt ASC")
    List<SubPedido> findSubPedidosComAtraso(@Param("tempo") LocalDateTime tempo);

    /**
     * Conta SubPedidos ativos por cozinha
     */
    @Query("SELECT COUNT(sp) FROM SubPedido sp WHERE sp.cozinha.id = :cozinhaId " +
           "AND sp.status IN ('PENDENTE', 'RECEBIDO', 'EM_PREPARACAO')")
    Long contarSubPedidosAtivosPorCozinha(@Param("cozinhaId") Long cozinhaId);
}
