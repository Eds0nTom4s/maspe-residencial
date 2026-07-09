package com.restaurante.financeiro.paymentmethod.entity;

import com.restaurante.model.entity.BaseEntity;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.PaymentConfirmationMode;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodProvider;
import com.restaurante.model.enums.PaymentMethodStatus;
import com.restaurante.model.enums.PaymentMethodType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "tenant_payment_methods", indexes = {
        @Index(name = "idx_tenant_payment_methods_tenant", columnList = "tenant_id"),
        @Index(name = "idx_tenant_payment_methods_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_tenant_payment_methods_tenant_code", columnList = "tenant_id, code")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TenantPaymentMethod extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 50, updatable = false)
    private PaymentMethodCode code;

    @NotNull
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "description", length = 255)
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentMethodStatus status = PaymentMethodStatus.INACTIVE;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50, updatable = false)
    private PaymentMethodType type;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_mode", nullable = false, length = 50, updatable = false)
    private PaymentConfirmationMode confirmationMode;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50, updatable = false)
    private PaymentMethodProvider provider;

    @NotNull
    @Column(name = "enabled_for_qr", nullable = false)
    private boolean enabledForQr = false;

    @NotNull
    @Column(name = "enabled_for_pos", nullable = false)
    private boolean enabledForPos = true;

    @NotNull
    @Column(name = "enabled_for_pedido", nullable = false)
    private boolean enabledForPedido = true;

    @NotNull
    @Column(name = "enabled_for_fundo_consumo", nullable = false)
    private boolean enabledForFundoConsumo = true;

    @NotNull
    @Column(name = "requires_open_turno", nullable = false, updatable = false)
    private boolean requiresOpenTurno = false;

    @NotNull
    @Column(name = "requires_gateway", nullable = false, updatable = false)
    private boolean requiresGateway = false;

    @NotNull
    @Column(name = "requires_manual_confirmation", nullable = false, updatable = false)
    private boolean requiresManualConfirmation = false;

    @Column(name = "min_amount", precision = 19, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 19, scale = 2)
    private BigDecimal maxAmount;

    @NotNull
    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "AOA";

    @NotNull
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 100;

    @Column(name = "icon_key", length = 80)
    private String iconKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson;
}
