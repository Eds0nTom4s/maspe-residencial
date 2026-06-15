package com.restaurante.financeiro.caixa.dto;

import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CaixaPedidoResponse {
    private Long pedidoId;
    private String pedidoNumero;
    private Long tenantId;
    private String origem;
    private String contexto;
    private Long instituicaoId;
    private Long unidadeAtendimentoId;
    private Long mesaId;
    private String mesaReferencia;
    private String clienteNome;
    private String clienteTelefoneMascarado;
    private StatusPedido operationalStatus;
    private StatusPedido statusOperacional;
    private StatusFinanceiroPedido paymentStatus;
    private StatusFinanceiroPedido statusFinanceiro;
    private PaymentMethodCode paymentMethod;
    private PaymentMethodCode metodoPagamento;
    private String paymentMethodDetail;
    private String ordemPagamentoStatus;
    private BigDecimal total;
    private BigDecimal valorPago;
    private BigDecimal saldoPendente;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime paidAt;
    private Boolean canConfirmPayment;
    private Boolean canReversePayment;
}
