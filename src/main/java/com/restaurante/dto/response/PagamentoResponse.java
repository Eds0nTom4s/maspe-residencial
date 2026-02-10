package com.restaurante.dto.response;

import com.restaurante.model.enums.MetodoPagamento;
import com.restaurante.model.enums.StatusPagamento;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de resposta para Pagamento
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagamentoResponse {

    private Long id;
    private Long unidadeConsumoId;
    private String referenciaUnidadeConsumo;
    private BigDecimal valor;
    private MetodoPagamento metodoPagamento;
    private StatusPagamento status;
    private String transactionId;
    private String paymentUrl;
    private String qrCodePix;
    private LocalDateTime processadoEm;
    private String observacoes;
    private LocalDateTime createdAt;
}
