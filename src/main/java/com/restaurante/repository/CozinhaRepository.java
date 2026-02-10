package com.restaurante.repository;

import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.enums.TipoCozinha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository para Cozinha
 */
@Repository
public interface CozinhaRepository extends JpaRepository<Cozinha, Long> {

    /**
     * Busca cozinhas ativas
     */
    List<Cozinha> findByAtivaTrue();

    /**
     * Busca cozinhas por tipo
     */
    List<Cozinha> findByTipo(TipoCozinha tipo);

    /**
     * Busca cozinhas ativas por tipo
     */
    List<Cozinha> findByAtivaAndTipo(Boolean ativa, TipoCozinha tipo);

    /**
     * Busca cozinha por nome
     */
    Optional<Cozinha> findByNomeIgnoreCase(String nome);

    /**
     * Busca cozinha por impressora
     */
    Optional<Cozinha> findByImpressoraId(String impressoraId);

    /**
     * Busca cozinhas vinculadas a uma unidade de atendimento
     */
    @Query("SELECT c FROM Cozinha c JOIN c.unidadesAtendimento ua " +
           "WHERE ua.id = :unidadeId AND c.tipo = :tipo AND c.ativa = :ativa")
    List<Cozinha> findByUnidadeAtendimentoIdAndTipoAndAtiva(
        @Param("unidadeId") Long unidadeId,
        @Param("tipo") TipoCozinha tipo,
        @Param("ativa") Boolean ativa);

    /**
     * Busca cozinhas vinculadas a uma unidade de atendimento
     */
    @Query("SELECT c FROM Cozinha c JOIN c.unidadesAtendimento ua WHERE ua.id = :unidadeId")
    List<Cozinha> findByUnidadeAtendimentoId(@Param("unidadeId") Long unidadeId);

    /**
     * Conta SubPedidos ativos por cozinha
     */
    @Query("SELECT COUNT(sp) FROM SubPedido sp WHERE sp.cozinha.id = :cozinhaId " +
           "AND sp.status IN ('PENDENTE', 'RECEBIDO', 'EM_PREPARACAO')")
    Long contarSubPedidosAtivosPorCozinha(@Param("cozinhaId") Long cozinhaId);
}
