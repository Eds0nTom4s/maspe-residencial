package com.restaurante.model.entity;

import com.restaurante.model.enums.MetodoPagamento;
import com.restaurante.model.enums.StatusPagamento;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidade Pagamento
 * Representa o pagamento de uma mesa
 * Estrutura preparada para integração futura com gateways de pagamento
 */
@Entity
@Table(name = "pagamentos", indexes = {
    @Index(name = "idx_pagamento_unidade_consumo", columnList = "unidade_consumo_id"),
    @Index(name = "idx_pagamento_status", columnList = "status"),
    @Index(name = "idx_pagamento_transaction_id", columnList = "transaction_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pagamento extends BaseEntity {

    // Relacionamento ONE-TO-ONE com UnidadeDeConsumo
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_consumo_id", nullable = false, unique = true)
    private UnidadeDeConsumo unidadeConsumo;

    @NotNull(message = "Valor é obrigatório")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pagamento", nullable = false, length = 30)
    private MetodoPagamento metodoPagamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusPagamento status = StatusPagamento.PENDENTE;

    @Column(name = "transaction_id", length = 100)
    private String transactionId; // ID da transação no gateway de pagamento

    @Column(name = "payment_url", length = 500)
    private String paymentUrl; // URL para pagamento digital (PIX, cartão)

    @Column(name = "qr_code_pix", columnDefinition = "TEXT")
    private String qrCodePix; // Código QR Code para pagamento PIX

    @Column(name = "processado_em")
    private LocalDateTime processadoEm;

    @Column(name = "gateway_response", columnDefinition = "TEXT")
    private String gatewayResponse; // Resposta do gateway de pagamento

    @Column(length = 500)
    private String observacoes;

    /**
     * Aprova o pagamento
     */
    public void aprovar() {
        this.status = StatusPagamento.APROVADO;
        this.processadoEm = LocalDateTime.now();
    }

    /**
     * Recusa o pagamento
     */
    public void recusar(String motivo) {
        this.status = StatusPagamento.RECUSADO;
        this.observacoes = motivo;
        this.processadoEm = LocalDateTime.now();
    }

    /**
     * Cancela o pagamento
     */
    public void cancelar(String motivo) {
        this.status = StatusPagamento.CANCELADO;
        this.observacoes = motivo;
        this.processadoEm = LocalDateTime.now();
    }

    /**
     * Verifica se o pagamento foi aprovado
     */
    public boolean isAprovado() {
        return status == StatusPagamento.APROVADO;
    }
}
