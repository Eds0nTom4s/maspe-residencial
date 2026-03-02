package com.restaurante.repository;

import com.restaurante.model.entity.Mesa;
import com.restaurante.model.enums.TipoUnidadeConsumo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
}
