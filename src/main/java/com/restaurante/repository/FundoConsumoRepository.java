package com.restaurante.repository;

import com.restaurante.model.entity.FundoConsumo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository para FundoConsumo.
 *
 * <p>O fundo é acessado sempre através da SessaoConsumo.
 * O token de acesso externo é o {@code qrCodeSessao} da sessão.
 */
@Repository
public interface FundoConsumoRepository extends JpaRepository<FundoConsumo, Long> {

    /**
     * Busca fundo ativo pela sessão de consumo.
     */
    @Query("SELECT f FROM FundoConsumo f WHERE f.sessaoConsumo.id = :sessaoId AND f.ativo = true")
    Optional<FundoConsumo> findBySessaoConsumoIdAndAtivoTrue(@Param("sessaoId") Long sessaoId);

    /**
     * Busca fundo (activo ou não) pela sessão de consumo.
     */
    @Query("SELECT f FROM FundoConsumo f WHERE f.sessaoConsumo.id = :sessaoId")
    Optional<FundoConsumo> findBySessaoConsumoId(@Param("sessaoId") Long sessaoId);

    /**
     * Busca fundo ativo pela sessão com finalidade de escrita/lock.
     * Agora utiliza implicitamente Optimistic Locking via @Version na entidade,
     * não necessitando do @Lock pessimista aqui.
     */
    @Query("SELECT f FROM FundoConsumo f WHERE f.sessaoConsumo.id = :sessaoId AND f.ativo = true")
    Optional<FundoConsumo> findBySessaoConsumoIdWithLock(@Param("sessaoId") Long sessaoId);

    /**
     * Verifica se sessão já tem fundo ativo.
     */
    @Query("SELECT COUNT(f) > 0 FROM FundoConsumo f WHERE f.sessaoConsumo.id = :sessaoId AND f.ativo = true")
    boolean existsBySessaoConsumoIdAndAtivoTrue(@Param("sessaoId") Long sessaoId);

    /**
     * Busca fundo pelo QR Code da sessão (token de acesso externo).
     * O qrCodeSessao da SessaoConsumo é o identificador público do fundo.
     */
    @Query("SELECT f FROM FundoConsumo f WHERE f.sessaoConsumo.qrCodeSessao = :qrCode AND f.ativo = true")
    Optional<FundoConsumo> findByQrCodeSessaoAndAtivoTrue(@Param("qrCode") String qrCode);

    /**
     * Busca fundo pelo QR Code da sessão com finalidade de escrita/lock optimista.
     */
    @Query("SELECT f FROM FundoConsumo f WHERE f.sessaoConsumo.qrCodeSessao = :qrCode AND f.ativo = true")
    Optional<FundoConsumo> findByQrCodeSessaoWithLock(@Param("qrCode") String qrCode);

    /**
     * Verifica se existe fundo ativo para o QR Code da sessão.
     */
    @Query("SELECT COUNT(f) > 0 FROM FundoConsumo f WHERE f.sessaoConsumo.qrCodeSessao = :qrCode AND f.ativo = true")
    boolean existsByQrCodeSessaoAndAtivoTrue(@Param("qrCode") String qrCode);

    /**
     * Busca fundo ativo pela Sessão do Cliente que está ABERTA, pesquisando pelo telefone (principal).
     */
    @Query("SELECT f FROM FundoConsumo f WHERE f.sessaoConsumo.cliente.telefone = :telefone AND f.sessaoConsumo.status = :status AND f.ativo = true")
    Optional<FundoConsumo> findBySessaoConsumoClienteTelefoneAndSessaoConsumoStatusAndAtivoTrue(@Param("telefone") String telefone, @Param("status") com.restaurante.model.enums.StatusSessaoConsumo status);
}
