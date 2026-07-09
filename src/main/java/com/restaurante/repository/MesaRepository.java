package com.restaurante.repository;

import com.restaurante.model.entity.Mesa;
import com.restaurante.model.enums.TipoUnidadeConsumo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import com.restaurante.repository.projection.SyncAggProjection;

/**
 * Repository para Mesa.
 *
 * <p>O status da mesa (DISPONÍVEL/OCUPADA) é DERIVADO — nunca persistido.
 * Use as queries {@link #findDisponiveis()} e {@link #findOcupadas()} para
 * obter mesas filtradas pelo status calculado em tempo real via SessaoConsumo.
 */
@Repository
public interface MesaRepository extends JpaRepository<Mesa, Long> {

    /**
     * Busca mesa pelo QR Code fixo.
     */
    Optional<Mesa> findByQrCode(String qrCode);

    /**
     * Busca mesa pela referência humana, ignorando maiúsculas/minúsculas.
     */
    Optional<Mesa> findByReferenciaIgnoreCase(String referencia);

    /**
     * Busca primeira mesa pelo número operacional.
     */
    Optional<Mesa> findFirstByNumero(Integer numero);

    /**
     * Lista mesas pelo flag de ativação administrativa.
     */
    List<Mesa> findByAtiva(Boolean ativa);

    /**
     * Lista mesas por tipo.
     */
    List<Mesa> findByTipo(TipoUnidadeConsumo tipo);

    /**
     * Lista mesas ativas de um tipo específico.
     */
    List<Mesa> findByAtivaAndTipo(Boolean ativa, TipoUnidadeConsumo tipo);

    /**
     * Lista todas as mesas de uma UnidadeAtendimento.
     */
    @Query("SELECT m FROM Mesa m WHERE m.unidadeAtendimento.id = :unidadeAtendimentoId")
    List<Mesa> findByUnidadeAtendimentoId(@Param("unidadeAtendimentoId") Long unidadeAtendimentoId);

    /**
     * Verifica se uma mesa está ocupada (possui SessaoConsumo ABERTA).
     * Este é o ponto central de verdade para o status da mesa.
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END " +
           "FROM SessaoConsumo s WHERE s.mesa.id = :mesaId AND s.status = 'ABERTA'")
    boolean isOcupada(@Param("mesaId") Long mesaId);

    /**
     * Retorna todas as mesas ATIVAS sem sessão aberta (status DISPONÍVEL derivado).
     */
    @Query("SELECT m FROM Mesa m WHERE m.ativa = true " +
           "AND NOT EXISTS (SELECT s FROM SessaoConsumo s WHERE s.mesa = m AND s.status = 'ABERTA')")
    List<Mesa> findDisponiveis();

    /**
     * Retorna todas as mesas ATIVAS com sessão aberta (status OCUPADA derivado).
     */
    @Query("SELECT m FROM Mesa m WHERE m.ativa = true " +
           "AND EXISTS (SELECT s FROM SessaoConsumo s WHERE s.mesa = m AND s.status = 'ABERTA')")
    List<Mesa> findOcupadas();

    /**
     * Conta mesas por flag de ativação.
     */
    long countByAtiva(Boolean ativa);

    /**
     * Conta mesas ocupadas de uma UnidadeAtendimento (status derivado).
     */
    @Query("SELECT COUNT(DISTINCT s.mesa) FROM SessaoConsumo s " +
           "WHERE s.mesa.unidadeAtendimento.id = :unidadeAtendimentoId AND s.status = 'ABERTA'")
    long contarOcupadasPorUnidadeAtendimento(@Param("unidadeAtendimentoId") Long unidadeAtendimentoId);

    @Query("SELECT m FROM Mesa m WHERE m.tenant.id = :tenantId " +
           "AND (:instituicaoId IS NULL OR m.instituicao.id = :instituicaoId) " +
           "AND (:unidadeAtendimentoId IS NULL OR m.unidadeAtendimento.id = :unidadeAtendimentoId) " +
           "AND (:ativa IS NULL OR m.ativa = :ativa)")
    List<Mesa> findByTenantIdWithFilters(
            @Param("tenantId") Long tenantId,
            @Param("instituicaoId") Long instituicaoId,
            @Param("unidadeAtendimentoId") Long unidadeAtendimentoId,
            @Param("ativa") Boolean ativa
    );

    @Query("SELECT m FROM Mesa m WHERE m.id = :id AND m.tenant.id = :tenantId")
    Optional<Mesa> findByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Mesa m WHERE m.id = :id")
    Optional<Mesa> findByIdForUpdate(@Param("id") Long id);

    List<Mesa> findByTenantId(Long tenantId);

    List<Mesa> findByTenantIdAndUpdatedAtAfter(Long tenantId, LocalDateTime updatedSince);

    List<Mesa> findByTenantIdAndUnidadeAtendimentoIdAndUpdatedAtAfter(Long tenantId, Long unidadeAtendimentoId, LocalDateTime updatedSince);

    long countByTenantIdAndUpdatedAtAfter(Long tenantId, LocalDateTime updatedSince);

    long countByTenantIdAndUnidadeAtendimentoIdAndUpdatedAtAfter(Long tenantId, Long unidadeAtendimentoId, LocalDateTime updatedSince);

    long countByTenantId(Long tenantId);

    @Query("""
            select count(m) as count,
                   max(m.updatedAt) as maxUpdatedAt,
                   max(m.createdAt) as maxCreatedAt,
                   sum(case when m.updatedAt is null then 1 else 0 end) as nullUpdatedAtCount
            from Mesa m
            where m.tenant.id = :tenantId
              and (cast(:unidadeAtendimentoId as string) is null or m.unidadeAtendimento.id = :unidadeAtendimentoId)
              and m.ativa = true
            """)
    SyncAggProjection computeSyncAgg(@Param("tenantId") Long tenantId, @Param("unidadeAtendimentoId") Long unidadeAtendimentoId);

    @Query("""
            select count(m)
            from Mesa m
            where m.tenant.id = :tenantId
              and (cast(:unidadeAtendimentoId as string) is null or m.unidadeAtendimento.id = :unidadeAtendimentoId)
              and m.ativa = true
            """)
    long countSyncByTenantAndScope(@Param("tenantId") Long tenantId, @Param("unidadeAtendimentoId") Long unidadeAtendimentoId);

    @Query("""
            select m
            from Mesa m
            where m.tenant.id = :tenantId
              and (cast(:unidadeAtendimentoId as string) is null or m.unidadeAtendimento.id = :unidadeAtendimentoId)
              and m.ativa = true
              and (cast(:updatedSince as timestamp) is null or m.updatedAt > :updatedSince)
              and (cast(:lastId as string) is null or m.id > :lastId)
            order by m.id asc
            """)
    List<Mesa> syncKeyset(@Param("tenantId") Long tenantId,
                          @Param("unidadeAtendimentoId") Long unidadeAtendimentoId,
                          @Param("updatedSince") LocalDateTime updatedSince,
                          @Param("lastId") Long lastId,
                          org.springframework.data.domain.Pageable pageable);
}
