package com.restaurante.device.capability.template.entity;

import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.DeviceCapabilityTemplateStatus;
import com.restaurante.model.enums.OperationalDeviceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "device_capability_templates",
        uniqueConstraints = @UniqueConstraint(name = "uk_device_cap_tpl", columnNames = {"tenant_id", "code"}),
        indexes = {
                @Index(name = "idx_device_cap_tpl_tenant_status", columnList = "tenant_id, status"),
                @Index(name = "idx_device_cap_tpl_tenant_target", columnList = "tenant_id, target_device_type")
        }
)
@Getter
@Setter
public class DeviceCapabilityTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "code", nullable = false, length = 80)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_device_type", length = 50)
    private OperationalDeviceType targetDeviceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DeviceCapabilityTemplateStatus status = DeviceCapabilityTemplateStatus.ACTIVE;

    @Column(name = "is_system_default", nullable = false)
    private boolean systemDefault = false;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by")
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

