package com.restaurante.dto.response;

import com.restaurante.financeiro.enums.MetodoPagamentoAppyPay;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.enums.TipoPagamentoFinanceiro;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Resposta para um Pagamento gateway (AppyPay).
 *
 * <h2>Campos de referência bancária (apenas REF)</h2>
 * <ul>
 *   <li>{@link #entidade} — entidade bancária (ex: "10100" para BAI)</li>
 *   <li>{@link #referencia} — referência de pagamento (ex: "999 123 456")</li>
 * </ul>
 * Para GPO, estes campos são {@code null}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagamentoGatewayResponse {

    private Long id;

    /** ID do fundo de consumo associado (recarga pré-paga). Null se for pagamento de pedido. */
    private Long fundoConsumoId;

    /** ID do pedido associado (pagamento pós-pago). Null se for recarga de fundo. */
    private Long pedidoId;

    private TipoPagamentoFinanceiro tipoPagamento;

    /** Método usado: GPO (instantâneo) ou REF (referência bancária). */
    private MetodoPagamentoAppyPay metodo;

    private BigDecimal amount;

    /** Estado actual: PENDENTE, CONFIRMADO, FALHOU, ESTORNADO. */
    private StatusPagamentoGateway status;

    /** Referência interna única (merchantTransactionId). */
    private String externalReference;

    /** Entidade bancária — apenas REF (ex: "10100"). */
    private String entidade;

    /** Referência de pagamento — apenas REF (ex: "999 123 456"). */
    private String referencia;

    /** Data/hora de confirmação (null se ainda PENDENTE). */
    private LocalDateTime confirmedAt;

    private LocalDateTime createdAt;
}
