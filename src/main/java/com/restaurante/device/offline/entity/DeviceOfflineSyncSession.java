package com.restaurante.device.offline.entity;

import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.DeviceOfflineSyncSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "device_offline_sync_sessions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_offline_sync_session", columnNames = {"tenant_id", "dispositivo_operacional_id", "sync_session_id"}),
                @UniqueConstraint(name = "uk_offline_server_sync_id", columnNames = {"tenant_id", "server_sync_id"})
        },
        indexes = {
                @Index(name = "idx_offline_sync_sessions_tenant_received", columnList = "tenant_id, received_at"),
                @Index(name = "idx_offline_sync_sessions_tenant_status", columnList = "tenant_id, status"),
                @Index(name = "idx_offline_sync_sessions_tenant_device", columnList = "tenant_id, dispositivo_operacional_id"),
                @Index(name = "idx_offline_sync_sessions_tenant_unidade", columnList = "tenant_id, unidade_atendimento_id"),
                @Index(name = "idx_offline_sync_sessions_app_version", columnList = "tenant_id, app_version"),
                @Index(name = "idx_offline_sync_sessions_server_sync_id", columnList = "tenant_id, server_sync_id")
        }
)
@Getter
@Setter
public class DeviceOfflineSyncSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_atendimento_id", nullable = false, updatable = false)
    private UnidadeAtendimento unidadeAtendimento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispositivo_operacional_id", nullable = false, updatable = false)
    private DispositivoOperacional dispositivoOperacional;

    @Column(name = "sync_session_id", nullable = false, length = 120)
    private String syncSessionId;

    @Column(name = "server_sync_id", nullable = false, length = 120, updatable = false)
    private String serverSyncId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DeviceOfflineSyncSessionStatus status;

    @Column(name = "app_version", length = 80)
    private String appVersion;

    @Column(name = "device_local_time")
    private Instant deviceLocalTime;

    @Column(name = "offline_started_at")
    private Instant offlineStartedAt;

    @Column(name = "offline_ended_at")
    private Instant offlineEndedAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "started_processing_at")
    private Instant startedProcessingAt;

    @Column(name = "finished_processing_at")
    private Instant finishedProcessingAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "total_commands", nullable = false)
    private int totalCommands;

    @Column(name = "applied_count", nullable = false)
    private int appliedCount;

    @Column(name = "duplicate_count", nullable = false)
    private int duplicateCount;

    @Column(name = "rejected_count", nullable = false)
    private int rejectedCount;

    @Column(name = "conflict_count", nullable = false)
    private int conflictCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "dependency_failed_count", nullable = false)
    private int dependencyFailedCount;

    @Column(name = "payload_limit_rejected_count", nullable = false)
    private int payloadLimitRejectedCount;

    @Column(name = "local_ref_count", nullable = false)
    private int localRefCount;

    @Column(name = "total_payload_bytes")
    private Integer totalPayloadBytes;

    @Column(name = "max_command_payload_bytes")
    private Integer maxCommandPayloadBytes;

    @Column(name = "client_ip", length = 100)
    private String clientIp;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "summary_json", columnDefinition = "jsonb")
    private String summaryJson;

    @Column(name = "error_summary_json", columnDefinition = "jsonb")
    private String errorSummaryJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (receivedAt == null) receivedAt = Instant.now();
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = DeviceOfflineSyncSessionStatus.RECEIVED;
        if (totalCommands < 0) totalCommands = 0;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

