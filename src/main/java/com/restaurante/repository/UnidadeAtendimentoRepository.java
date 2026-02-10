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
    @Query("SELECT u FROM UnidadeAtendimento u JOIN u.cozinhas c WHERE u.ativa = true AND c.ativa = true")
    List<UnidadeAtendimento> findUnidadesOperacionais();

    /**
     * Conta unidades de consumo ativas vinculadas a uma unidade de atendimento
     */
    @Query("SELECT COUNT(uc) FROM UnidadeDeConsumo uc " +
           "WHERE uc.unidadeAtendimento.id = :unidadeId AND uc.status IN ('OCUPADA', 'AGUARDANDO_PAGAMENTO')")
    Long contarUnidadesConsumoAtivasPorUnidade(@Param("unidadeId") Long unidadeId);
}
