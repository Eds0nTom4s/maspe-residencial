package com.restaurante.dto.response;

import com.restaurante.model.enums.TipoTransacaoFundo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de resposta para uma transação do Fundo de Consumo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransacaoFundoResponse {

    private Long id;

    /** CREDITO | DEBITO | ESTORNO */
    private TipoTransacaoFundo tipo;

    /** Valor movimentado (sempre positivo) */
    private BigDecimal valor;

    /** Saldo do fundo imediatamente antes da transação */
    private BigDecimal saldoAnterior;

    /** Saldo do fundo imediatamente após a transação */
    private BigDecimal saldoNovo;

    /** ID do pedido relacionado (nulo para recargas) */
    private Long pedidoId;

    private String observacoes;
    private LocalDateTime createdAt;
}
