package com.restaurante.model.entity;

import com.restaurante.model.enums.OrdemPagamentoManualIdempotencyStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tenant_payment_confirmation_idempotency_records", indexes = {
        @Index(name = "idx_tenant_payment_idem_actor", columnList = "tenant_id, user_id"),
        @Index(name = "idx_tenant_payment_idem_order", columnList = "ordem_pagamento_id"),
        @Index(name = "idx_tenant_payment_idem_created", columnList = "created_at")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TenantPaymentConfirmationIdempotencyRecord extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ordem_pagamento_id", nullable = false)
    private OrdemPagamento ordemPagamento;

    @NotBlank
    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @NotBlank
    @Column(name = "client_request_id", nullable = false, length = 160)
    private String clientRequestId;

    @NotBlank
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrdemPagamentoManualIdempotencyStatus status = OrdemPagamentoManualIdempotencyStatus.IN_PROGRESS;
}
