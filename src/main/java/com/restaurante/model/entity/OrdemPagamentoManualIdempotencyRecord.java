package com.restaurante.model.entity;

import com.restaurante.model.enums.OrdemPagamentoManualIdempotencyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ordem_pagamento_manual_idempotency_records", indexes = {
        @Index(name = "idx_ordem_pg_idem_tenant_device", columnList = "tenant_id, device_id"),
        @Index(name = "idx_ordem_pg_idem_ordem", columnList = "ordem_pagamento_id"),
        @Index(name = "idx_ordem_pg_idem_created_at", columnList = "created_at")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class OrdemPagamentoManualIdempotencyRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private DispositivoOperacional dispositivo;

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

    @Column(name = "error_code", length = 80)
    private String errorCode;
}

