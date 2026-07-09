package com.restaurante.dto.response;

import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.PaymentMethodCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CaixaOperadorSessionItemResponse {
    private Long id;
    private Long ordemPagamentoId;
    private Long pagamentoId;
    private Long pedidoId;
    private Long sessaoConsumoId;
    private PaymentMethodCode paymentMethod;
    private BigDecimal amount;
    private LocalDateTime confirmedAt;
    private OperationalOrigem source;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrdemPagamentoId() { return ordemPagamentoId; }
    public void setOrdemPagamentoId(Long ordemPagamentoId) { this.ordemPagamentoId = ordemPagamentoId; }
    public Long getPagamentoId() { return pagamentoId; }
    public void setPagamentoId(Long pagamentoId) { this.pagamentoId = pagamentoId; }
    public Long getPedidoId() { return pedidoId; }
    public void setPedidoId(Long pedidoId) { this.pedidoId = pedidoId; }
    public Long getSessaoConsumoId() { return sessaoConsumoId; }
    public void setSessaoConsumoId(Long sessaoConsumoId) { this.sessaoConsumoId = sessaoConsumoId; }
    public PaymentMethodCode getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethodCode paymentMethod) { this.paymentMethod = paymentMethod; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public OperationalOrigem getSource() { return source; }
    public void setSource(OperationalOrigem source) { this.source = source; }
}

