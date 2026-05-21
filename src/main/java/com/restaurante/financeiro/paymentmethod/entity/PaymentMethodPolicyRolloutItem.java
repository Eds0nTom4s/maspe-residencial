package com.restaurante.financeiro.paymentmethod.entity;

import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "payment_method_policy_rollout_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_rollout_item", columnNames = {"tenant_id", "rollout_id", "dispositivo_operacional_id", "payment_method_code"})
)
@Getter
@Setter
public class PaymentMethodPolicyRolloutItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rollout_id", nullable = false, updatable = false)
    private PaymentMethodPolicyRollout rollout;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false, updatable = false)
    private PaymentMethodPolicyTemplate template;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_atendimento_id", nullable = false, updatable = false)
    private UnidadeAtendimento unidadeAtendimento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispositivo_operacional_id", nullable = false, updatable = false)
    private DispositivoOperacional dispositivoOperacional;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method_code", nullable = false, updatable = false, length = 50)
    private PaymentMethodCode paymentMethodCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "planned_action", nullable = false, length = 50)
    private PaymentMethodPolicyRolloutItemAction plannedAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentMethodPolicyRolloutItemStatus status = PaymentMethodPolicyRolloutItemStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "overwrite_mode", nullable = false, length = 50)
    private PaymentMethodPolicyOverwriteMode overwriteMode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_policy_id")
    private DevicePaymentMethodPolicy previousPolicy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resulting_policy_id")
    private DevicePaymentMethodPolicy resultingPolicy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_item_id")
    private PaymentMethodPolicyTemplateItem templateItem;

    @Column(name = "manual_override_detected", nullable = false)
    private boolean manualOverrideDetected;

    @Enumerated(EnumType.STRING)
    @Column(name = "skipped_reason", length = 100)
    private PaymentMethodPolicyRolloutSkippedReason skippedReason;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

