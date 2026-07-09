package com.restaurante.device.capability.entity;

import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.DeviceCapability;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "device_operational_capabilities",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_device_capability",
                columnNames = {"tenant_id", "dispositivo_operacional_id", "capability"}
        ),
        indexes = {
                @Index(name = "idx_device_cap_tenant_device", columnList = "tenant_id, dispositivo_operacional_id"),
                @Index(name = "idx_device_cap_tenant_capability", columnList = "tenant_id, capability")
        }
)
@Getter
@Setter
public class DeviceOperationalCapabilityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispositivo_operacional_id", nullable = false, updatable = false)
    private DispositivoOperacional dispositivoOperacional;

    @Enumerated(EnumType.STRING)
    @Column(name = "capability", nullable = false, length = 80)
    private DeviceCapability capability;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "source", nullable = false, length = 50)
    private String source = "MANUAL";

    @Column(name = "source_template_id")
    private Long sourceTemplateId;

    @Column(name = "source_rollout_id")
    private Long sourceRolloutId;

    @Column(name = "template_managed", nullable = false)
    private boolean templateManaged = false;

    @Column(name = "manual_override", nullable = false)
    private boolean manualOverride = false;

    @Column(name = "template_applied_at")
    private Instant templateAppliedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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
