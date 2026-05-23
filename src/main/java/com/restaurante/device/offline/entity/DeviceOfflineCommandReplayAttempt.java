package com.restaurante.device.offline.entity;

import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "device_offline_command_replay_attempts",
        indexes = {
                @Index(name = "idx_offline_replay_attempts_tenant_session", columnList = "tenant_id, server_sync_id, requested_at"),
                @Index(name = "idx_offline_replay_attempts_command", columnList = "tenant_id, device_offline_command_id, requested_at"),
                @Index(name = "idx_offline_replay_attempts_status", columnList = "tenant_id, replay_status, requested_at")
        }
)
@Getter
@Setter
public class DeviceOfflineCommandReplayAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sync_session_db_id", nullable = false, updatable = false)
    private DeviceOfflineSyncSession syncSession;

    @Column(name = "server_sync_id", nullable = false, length = 120, updatable = false)
    private String serverSyncId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_offline_command_id", nullable = false, updatable = false)
    private DeviceOfflineCommand command;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispositivo_operacional_id", nullable = false, updatable = false)
    private DispositivoOperacional dispositivoOperacional;

    @Column(name = "client_request_id", nullable = false, length = 120, updatable = false)
    private String clientRequestId;

    @Column(name = "command_type", nullable = false, length = 80, updatable = false)
    private String commandType;

    @Column(name = "previous_status", nullable = false, length = 30, updatable = false)
    private String previousStatus;

    @Column(name = "replay_status", nullable = false, length = 30)
    private String replayStatus;

    @Column(name = "eligibility_status", nullable = false, length = 30)
    private String eligibilityStatus;

    @Column(name = "eligibility_reason", length = 120)
    private String eligibilityReason;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber = 1;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private Long requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "result_status", length = 30)
    private String resultStatus;

    @Column(name = "result_json", columnDefinition = "jsonb")
    private String resultJson;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "audit_reason", length = 255)
    private String auditReason;

    @Column(name = "created_entity_type", length = 80)
    private String createdEntityType;

    @Column(name = "created_entity_id")
    private Long createdEntityId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (requestedAt == null) requestedAt = Instant.now();
        if (createdAt == null) createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

