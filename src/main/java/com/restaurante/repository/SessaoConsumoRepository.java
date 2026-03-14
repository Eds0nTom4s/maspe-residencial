package com.restaurante.repository;

import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.enums.StatusSessaoConsumo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository para SessaoConsumo.
 *
 * <p>Regra fundamental: quando mesa presente, apenas UMA sessão ABERTA por mesa.
 * Use {@link #existsByMesaIdAndStatus} para validar antes de abrir nova sessão.
 */
@Repository
public interface SessaoConsumoRepository extends JpaRepository<SessaoConsumo, Long> {

    /**
     * Busca sessão pelo QR Code único da sessão.
     * Principal ponto de entrada por token externo.
     */
    Optional<SessaoConsumo> findByQrCodeSessao(String qrCodeSessao);

    /**
     * Busca a sessão de uma mesa com determinado status.
     * Principal uso: buscar a sessão ABERTA da mesa para vincular pedidos.
     */
    Optional<SessaoConsumo> findByMesaIdAndStatus(Long mesaId, StatusSessaoConsumo status);

    /**
     * Histórico de sessões de uma mesa, ordenado da mais recente para a mais antiga.
     */
    List<SessaoConsumo> findByMesaIdOrderByAbertaEmDesc(Long mesaId);

    /**
     * Lista todas as sessões em determinado status.
     */
    List<SessaoConsumo> findByStatus(StatusSessaoConsumo status);

    /**
     * Busca sessão ABERTA de um cliente identificado.
     * Usado para impedir que o mesmo cliente tenha duas sessões abertas simultaneamente.
     */
    @Query("SELECT s FROM SessaoConsumo s WHERE s.cliente.id = :clienteId AND s.status = 'ABERTA'")
    Optional<SessaoConsumo> findSessaoAbertaByCliente(@Param("clienteId") Long clienteId);

    /**
     * Verifica se uma mesa possui sessão com determinado status.
     * Usado como guarda de negócio antes de abrir nova sessão.
     */
    boolean existsByMesaIdAndStatus(Long mesaId, StatusSessaoConsumo status);

    /**
     * Conta sessões por status.
     */
    long countByStatus(StatusSessaoConsumo status);

    /**
     * Conta sessões abertas em mesas de uma UnidadeAtendimento.
     */
    @Query("SELECT COUNT(s) FROM SessaoConsumo s " +
           "WHERE s.mesa.unidadeAtendimento.id = :unidadeAtendimentoId AND s.status = 'ABERTA'")
    long countAbertasByUnidadeAtendimento(@Param("unidadeAtendimentoId") Long unidadeAtendimentoId);

    /**
     * Busca sessões com status específico que foram abertas ANTES de determinada data.
     * Utilizado pelo Scheduler para inativar (EXPIRAR) sessões zumbi.
     */
    List<SessaoConsumo> findByStatusAndAbertaEmBefore(StatusSessaoConsumo status, LocalDateTime data);
}
