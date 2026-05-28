package com.restaurante.device.capability.template.entity;

import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.DeviceCapabilityOverwriteMode;
import com.restaurante.model.enums.DeviceCapabilityRolloutMode;
import com.restaurante.model.enums.DeviceCapabilityRolloutStatus;
import com.restaurante.model.enums.OperationalDeviceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "device_capability_rollouts",
        indexes = {
                @Index(name = "idx_device_cap_rollout_tenant", columnList = "tenant_id, id"),
                @Index(name = "idx_device_cap_rollout_tpl", columnList = "tenant_id, template_id")
        }
)
@Getter
@Setter
public class DeviceCapabilityRollout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false, updatable = false)
    private DeviceCapabilityTemplate template;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_atendimento_id", nullable = false, updatable = false)
    private UnidadeAtendimento unidadeAtendimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "rollout_mode", nullable = false, length = 50)
    private DeviceCapabilityRolloutMode rolloutMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "overwrite_mode", nullable = false, length = 50)
    private DeviceCapabilityOverwriteMode overwriteMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_device_type", length = 50)
    private OperationalDeviceType targetDeviceType;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DeviceCapabilityRolloutStatus status;

    @Column(name = "total_devices_targeted", nullable = false)
    private int totalDevicesTargeted = 0;

    @Column(name = "total_capabilities_created", nullable = false)
    private int totalCapabilitiesCreated = 0;

    @Column(name = "total_capabilities_updated", nullable = false)
    private int totalCapabilitiesUpdated = 0;

    @Column(name = "total_capabilities_skipped", nullable = false)
    private int totalCapabilitiesSkipped = 0;

    @Column(name = "total_errors", nullable = false)
    private int totalErrors = 0;

    @Column(name = "result_json", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String resultJson;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @PrePersist
    void onCreate() {
        if (startedAt == null) startedAt = Instant.now();
    }
}

