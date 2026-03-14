package com.restaurante.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de resposta para Fundo de Consumo (pré-pago)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundoConsumoResponse {

    private Long id;

    /** Saldo disponível em AOA */
    private BigDecimal saldoAtual;

    /** true = pode receber débitos; false = encerrado */
    private Boolean ativo;

    /** ID da sessão de consumo à qual este fundo pertence. */
    private Long sessaoId;

    /** Token público UUID da sessão — identificador externo do fundo. */
    private String qrCodeSessao;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
