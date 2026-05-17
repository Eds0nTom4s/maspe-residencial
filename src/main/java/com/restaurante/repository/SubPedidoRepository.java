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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    // ---------------------------------------------------------------------
    // Tenant-scoped + Produção (Prompt 17)
    // ---------------------------------------------------------------------

    Optional<SubPedido> findByIdAndTenantId(Long id, Long tenantId);

    @Query("SELECT sp FROM SubPedido sp " +
           "WHERE sp.tenant.id = :tenantId " +
           "AND sp.unidadeProducao.id = :unidadeProducaoId " +
           "ORDER BY sp.createdAt DESC")
    List<SubPedido> findByTenantIdAndUnidadeProducaoIdOrderByCreatedAtDesc(
            @Param("tenantId") Long tenantId,
            @Param("unidadeProducaoId") Long unidadeProducaoId);

    @Query("SELECT sp FROM SubPedido sp " +
           "WHERE sp.tenant.id = :tenantId " +
           "AND sp.unidadeProducao.id = :unidadeProducaoId " +
           "AND (:status IS NULL OR sp.status = :status) " +
           "ORDER BY sp.createdAt DESC")
    List<SubPedido> findByTenantIdAndUnidadeProducaoIdAndStatusOrderByCreatedAtDesc(
            @Param("tenantId") Long tenantId,
            @Param("unidadeProducaoId") Long unidadeProducaoId,
            @Param("status") StatusSubPedido status);

    // ---------------------------------------------------------------------
    // KDS-ready paging queries (Prompt 22)
    // ---------------------------------------------------------------------

    @Query("""
            select sp.id from SubPedido sp
              join sp.pedido p
            where sp.tenant.id = :tenantId
              and sp.unidadeProducao.id = :unidadeProducaoId
              and (:status is null or sp.status = :status)
              and (:de is null or sp.createdAt >= :de)
              and (:ate is null or sp.createdAt <= :ate)
              and (
                    :search is null or :search = '' or
                    lower(p.numero) like lower(concat('%', :search, '%')) or
                    lower(sp.numero) like lower(concat('%', :search, '%'))
                  )
            order by sp.createdAt asc
            """)
    Page<Long> findKdsIdsByTenantAndUnidadeAndFilters(
            @Param("tenantId") Long tenantId,
            @Param("unidadeProducaoId") Long unidadeProducaoId,
            @Param("status") StatusSubPedido status,
            @Param("de") LocalDateTime de,
            @Param("ate") LocalDateTime ate,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
            select distinct sp from SubPedido sp
              join fetch sp.pedido p
              left join fetch p.sessaoConsumo sc
              left join fetch sc.mesa m
              left join fetch sp.unidadeProducao up
              left join fetch sp.itens i
              left join fetch i.produto prod
            where sp.id in :ids
            """)
    List<SubPedido> findKdsDetailsByIdIn(@Param("ids") List<Long> ids);

    @Query("""
            select sp.id from SubPedido sp
              join sp.pedido p
            where sp.tenant.id = :tenantId
              and (:unidadeProducaoId is null or sp.unidadeProducao.id = :unidadeProducaoId)
              and (:status is null or sp.status = :status)
              and (:de is null or sp.createdAt >= :de)
              and (:ate is null or sp.createdAt <= :ate)
              and (:pedidoNumero is null or :pedidoNumero = '' or lower(p.numero) like lower(concat('%', :pedidoNumero, '%')))
            order by sp.createdAt asc
            """)
    Page<Long> findKdsIdsByTenantAndFilters(
            @Param("tenantId") Long tenantId,
            @Param("unidadeProducaoId") Long unidadeProducaoId,
            @Param("status") StatusSubPedido status,
            @Param("de") LocalDateTime de,
            @Param("ate") LocalDateTime ate,
            @Param("pedidoNumero") String pedidoNumero,
            Pageable pageable
    );

    interface SubPedidoMetricRow {
        Long getUnidadeProducaoId();
        String getUnidadeProducaoNome();
        StatusSubPedido getStatus();
        LocalDateTime getCreatedAt();
        LocalDateTime getIniciadoEm();
        LocalDateTime getProntoEm();
        LocalDateTime getEntregueEm();
    }

    @Query("""
            select
              up.id as unidadeProducaoId,
              up.nome as unidadeProducaoNome,
              sp.status as status,
              sp.createdAt as createdAt,
              sp.iniciadoEm as iniciadoEm,
              sp.prontoEm as prontoEm,
              sp.entregueEm as entregueEm
            from SubPedido sp
              left join sp.unidadeProducao up
            where sp.tenant.id = :tenantId
              and (:unidadeProducaoId is null or up.id = :unidadeProducaoId)
              and (:de is null or sp.createdAt >= :de)
              and (:ate is null or sp.createdAt <= :ate)
            """)
    List<SubPedidoMetricRow> findMetricRowsByTenantAndPeriod(
            @Param("tenantId") Long tenantId,
            @Param("unidadeProducaoId") Long unidadeProducaoId,
            @Param("de") LocalDateTime de,
            @Param("ate") LocalDateTime ate
    );
}
