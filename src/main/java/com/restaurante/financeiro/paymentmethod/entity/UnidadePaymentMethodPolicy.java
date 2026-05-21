package com.restaurante.financeiro.paymentmethod.entity;

import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodPolicyStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "unidade_payment_method_policies",
        uniqueConstraints = @UniqueConstraint(name = "uk_unidade_payment_method_policy", columnNames = {"tenant_id", "unidade_atendimento_id", "payment_method_code"})
)
@Getter
@Setter
public class UnidadePaymentMethodPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_atendimento_id", nullable = false, updatable = false)
    private UnidadeAtendimento unidadeAtendimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method_code", nullable = false, updatable = false, length = 50)
    private PaymentMethodCode paymentMethodCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentMethodPolicyStatus status;

    @Column(name = "enabled_for_qr")
    private Boolean enabledForQr;

    @Column(name = "enabled_for_pos")
    private Boolean enabledForPos;

    @Column(name = "enabled_for_pedido")
    private Boolean enabledForPedido;

    @Column(name = "enabled_for_fundo_consumo")
    private Boolean enabledForFundoConsumo;

    @Column(name = "min_amount", precision = 19, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 19, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "inherit_from_tenant", nullable = false)
    private boolean inheritFromTenant = true;

    @Column(name = "override_reason", length = 255)
    private String overrideReason;

    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

