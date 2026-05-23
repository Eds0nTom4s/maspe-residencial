package com.restaurante.device.capability.template.entity;

import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.DeviceCapability;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "device_capability_template_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_device_cap_tpl_item", columnNames = {"tenant_id", "template_id", "capability"})
)
@Getter
@Setter
public class DeviceCapabilityTemplateItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false, updatable = false)
    private DeviceCapabilityTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(name = "capability", nullable = false, length = 80)
    private DeviceCapability capability;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "override_reason", length = 255)
    private String overrideReason;

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

