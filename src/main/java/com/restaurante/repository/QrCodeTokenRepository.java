package com.restaurante.repository;

import com.restaurante.model.entity.QrCodeToken;
import com.restaurante.model.enums.StatusQrCode;
import com.restaurante.model.enums.TipoQrCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository para QrCodeToken
 */
@Repository
public interface QrCodeTokenRepository extends JpaRepository<QrCodeToken, Long> {

    /**
     * Busca QR Code por token
     */
    Optional<QrCodeToken> findByToken(String token);

    /**
     * Busca QR Codes ativos por Unidade de Consumo
     */
    List<QrCodeToken> findByUnidadeDeConsumoIdAndStatus(Long unidadeDeConsumoId, StatusQrCode status);

    /**
     * Busca QR Code ativo de mesa por Unidade de Consumo
     */
    Optional<QrCodeToken> findByUnidadeDeConsumoIdAndTipoAndStatus(
            Long unidadeDeConsumoId, 
            TipoQrCode tipo, 
            StatusQrCode status
    );

    /**
     * Busca QR Codes por Pedido
     */
    List<QrCodeToken> findByPedidoId(Long pedidoId);

    /**
     * Busca QR Codes por tipo e status
     */
    List<QrCodeToken> findByTipoAndStatus(TipoQrCode tipo, StatusQrCode status);

    /**
     * Busca QR Codes expirados
     */
    @Query("SELECT q FROM QrCodeToken q WHERE q.status = 'ATIVO' AND q.expiraEm < :agora")
    List<QrCodeToken> findExpirados(@Param("agora") LocalDateTime agora);

    /**
     * Busca QR Codes que expiram em breve (próximas X horas)
     */
    @Query("SELECT q FROM QrCodeToken q WHERE q.status = 'ATIVO' " +
           "AND q.expiraEm BETWEEN :agora AND :limite " +
           "AND q.tipo = 'MESA'")
    List<QrCodeToken> findExpirandoEmBreve(
            @Param("agora") LocalDateTime agora,
            @Param("limite") LocalDateTime limite
    );

    /**
     * Conta QR Codes ativos por tipo
     */
    long countByTipoAndStatus(TipoQrCode tipo, StatusQrCode status);

    /**
     * Busca todos os QR Codes de uma Unidade de Atendimento
     */
    @Query("SELECT q FROM QrCodeToken q " +
           "JOIN q.unidadeDeConsumo uc " +
           "WHERE uc.unidadeAtendimento.id = :unidadeAtendimentoId")
    List<QrCodeToken> findByUnidadeAtendimentoId(@Param("unidadeAtendimentoId") Long unidadeAtendimentoId);

    /**
     * Deleta QR Codes expirados há mais de X dias
     */
    @Query("DELETE FROM QrCodeToken q WHERE q.status IN ('EXPIRADO', 'USADO') " +
           "AND q.expiraEm < :dataLimite")
    void deleteExpiradosAntigos(@Param("dataLimite") LocalDateTime dataLimite);
}
