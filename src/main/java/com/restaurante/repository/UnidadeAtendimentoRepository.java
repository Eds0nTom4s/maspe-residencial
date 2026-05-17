package com.restaurante.repository;

import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository para UnidadeAtendimento
 */
@Repository
public interface UnidadeAtendimentoRepository extends JpaRepository<UnidadeAtendimento, Long> {

    /**
     * Busca unidades ativas
     */
    @Query("SELECT DISTINCT u FROM UnidadeAtendimento u LEFT JOIN FETCH u.cozinhas")
    List<UnidadeAtendimento> findAllWithCozinhas();

    /**
     * Busca unidades ativas com cozinhas inicializadas
     */
    @Query("SELECT DISTINCT u FROM UnidadeAtendimento u LEFT JOIN FETCH u.cozinhas WHERE u.ativa = true")
    List<UnidadeAtendimento> findByAtivaTrueWithCozinhas();

    /**
     * Busca unidades por tipo com cozinhas inicializadas
     */
    @Query("SELECT DISTINCT u FROM UnidadeAtendimento u LEFT JOIN FETCH u.cozinhas WHERE u.tipo = :tipo")
    List<UnidadeAtendimento> findByTipoWithCozinhas(@Param("tipo") TipoUnidadeAtendimento tipo);

    /**
     * Busca unidade por ID com cozinhas inicializadas
     */
    @Query("SELECT DISTINCT u FROM UnidadeAtendimento u LEFT JOIN FETCH u.cozinhas WHERE u.id = :id")
    Optional<UnidadeAtendimento> findByIdWithCozinhas(@Param("id") Long id);

    /**
     * Busca unidades ativas
     */
    List<UnidadeAtendimento> findByAtivaTrue();

    /**
     * Busca unidades por tipo
     */
    List<UnidadeAtendimento> findByTipo(TipoUnidadeAtendimento tipo);

    /**
     * Busca unidades ativas por tipo
     */
    List<UnidadeAtendimento> findByAtivaAndTipo(Boolean ativa, TipoUnidadeAtendimento tipo);

    /**
     * Busca unidade por nome
     */
    Optional<UnidadeAtendimento> findByNomeIgnoreCase(String nome);

    /**
     * Busca unidades operacionais (ativas e com cozinhas)
     */
    @Query("SELECT DISTINCT u FROM UnidadeAtendimento u JOIN FETCH u.cozinhas c WHERE u.ativa = true AND c.ativa = true")
    List<UnidadeAtendimento> findUnidadesOperacionais();

    /**
     * Conta unidades de consumo ativas vinculadas a uma unidade de atendimento
     */
    @Query("SELECT COUNT(uc) FROM UnidadeDeConsumo uc " +
           "WHERE uc.unidadeAtendimento.id = :unidadeId AND uc.status IN ('OCUPADA', 'AGUARDANDO_PAGAMENTO')")
    Long contarUnidadesConsumoAtivasPorUnidade(@Param("unidadeId") Long unidadeId);

    @Query("SELECT COUNT(u) FROM UnidadeAtendimento u WHERE u.instituicao.tenant.id = :tenantId")
    long countByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT u FROM UnidadeAtendimento u WHERE u.instituicao.tenant.id = :tenantId")
    List<UnidadeAtendimento> findByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT u FROM UnidadeAtendimento u WHERE u.instituicao.tenant.id = :tenantId AND u.id = :id")
    Optional<UnidadeAtendimento> findByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Query("SELECT u FROM UnidadeAtendimento u WHERE u.instituicao.tenant.id = :tenantId AND (:instituicaoId IS NULL OR u.instituicao.id = :instituicaoId) AND (:ativa IS NULL OR u.ativa = :ativa)")
    List<UnidadeAtendimento> findByTenantIdWithFilters(
            @Param("tenantId") Long tenantId,
            @Param("instituicaoId") Long instituicaoId,
            @Param("ativa") Boolean ativa
    );
}
