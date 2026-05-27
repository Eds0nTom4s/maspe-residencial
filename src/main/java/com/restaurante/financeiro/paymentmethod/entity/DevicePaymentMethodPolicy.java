package com.restaurante.financeiro.paymentmethod.entity;

import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodPolicyStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "device_payment_method_policies",
        uniqueConstraints = @UniqueConstraint(name = "uk_device_payment_method_policy", columnNames = {"tenant_id", "dispositivo_operacional_id", "payment_method_code"})
)
@Getter
@Setter
public class DevicePaymentMethodPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispositivo_operacional_id", nullable = false, updatable = false)
    private DispositivoOperacional dispositivoOperacional;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_atendimento_id", nullable = false, updatable = false)
    private UnidadeAtendimento unidadeAtendimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method_code", nullable = false, updatable = false, length = 50)
    private PaymentMethodCode paymentMethodCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentMethodPolicyStatus status;

    @Column(name = "enabled_for_pos")
    private Boolean enabledForPos;

    @Column(name = "enabled_for_pedido")
    private Boolean enabledForPedido;

    @Column(name = "enabled_for_fundo_consumo")
    private Boolean enabledForFundoConsumo;

    @Column(name = "can_confirm_manual")
    private Boolean canConfirmManual;

    @Column(name = "can_start_gateway")
    private Boolean canStartGateway;

    @Column(name = "min_amount", precision = 19, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 19, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "inherit_from_unidade", nullable = false)
    private boolean inheritFromUnidade = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_template_id")
    private PaymentMethodPolicyTemplate sourceTemplate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_rollout_id")
    private PaymentMethodPolicyRollout sourceRollout;

    @Column(name = "template_managed", nullable = false)
    private boolean templateManaged = false;

    @Column(name = "manual_override", nullable = false)
    private boolean manualOverride = false;

    @Column(name = "template_applied_at")
    private Instant templateAppliedAt;

    @Column(name = "override_reason", length = 255)
    private String overrideReason;

    @JdbcTypeCode(SqlTypes.JSON)
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
