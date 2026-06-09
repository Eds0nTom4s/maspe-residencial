package com.restaurante.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_operational_modules_configs", indexes = {
        @Index(name = "uq_tenant_operational_modules_tenant", columnList = "tenant_id", unique = true)
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TenantOperationalModulesConfig extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "sessao_consumo_enabled", nullable = false)
    private boolean sessaoConsumoEnabled = true;

    @Column(name = "pedido_direto_enabled", nullable = false)
    private boolean pedidoDiretoEnabled = true;

    @Column(name = "mesas_enabled", nullable = false)
    private boolean mesasEnabled = true;

    @Column(name = "qr_mesa_enabled", nullable = false)
    private boolean qrMesaEnabled = true;

    @Column(name = "caixa_enabled", nullable = false)
    private boolean caixaEnabled = true;

    @Column(name = "kds_enabled", nullable = false)
    private boolean kdsEnabled = true;

    @Column(name = "configured_by_platform_user_id")
    private Long configuredByPlatformUserId;

    @Column(name = "configured_at")
    private LocalDateTime configuredAt;
}
