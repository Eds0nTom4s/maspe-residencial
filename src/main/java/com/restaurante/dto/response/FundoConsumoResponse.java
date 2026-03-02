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

    /**
     * Fluxo identificado: ID do cliente proprietário.
     * Nulo no fluxo anónimo.
     */
    private Long clienteId;

    /**
     * Fluxo anónimo: token UUID do QR Code portador.
     * Nulo no fluxo identificado.
     */
    private String tokenPortador;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
