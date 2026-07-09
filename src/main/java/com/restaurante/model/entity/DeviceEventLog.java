package com.restaurante.model.entity;

import com.restaurante.model.enums.DeviceEventStatus;
import com.restaurante.model.enums.DeviceEventType;
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

@Entity
@Table(name = "device_event_logs", indexes = {
        @Index(name = "idx_device_event_tenant", columnList = "tenant_id"),
        @Index(name = "idx_device_event_tenant_created_at", columnList = "tenant_id, created_at"),
        @Index(name = "idx_device_event_device", columnList = "dispositivo_id"),
        @Index(name = "idx_device_event_event_type", columnList = "event_type")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class DeviceEventLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispositivo_id", nullable = false)
    private DispositivoOperacional dispositivo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private DeviceEventType eventType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeviceEventStatus status;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;
}

