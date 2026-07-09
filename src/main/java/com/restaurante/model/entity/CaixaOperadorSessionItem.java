package com.restaurante.model.entity;

import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.PaymentMethodCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "caixa_operador_session_items", indexes = {
        @Index(name = "idx_caixa_items_caixa", columnList = "tenant_id, caixa_operador_session_id"),
        @Index(name = "idx_caixa_items_pagamento", columnList = "tenant_id, pagamento_id"),
        @Index(name = "idx_caixa_items_pedido", columnList = "tenant_id, pedido_id"),
        @Index(name = "idx_caixa_items_method", columnList = "tenant_id, payment_method")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class CaixaOperadorSessionItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caixa_operador_session_id", nullable = false)
    private CaixaOperadorSession caixaOperadorSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ordem_pagamento_id", nullable = false)
    private OrdemPagamento ordemPagamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pagamento_id")
    private Pagamento pagamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_consumo_id")
    private SessaoConsumo sessaoConsumo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethodCode paymentMethod;

    @NotNull
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 40)
    private OperationalOrigem source;
}

