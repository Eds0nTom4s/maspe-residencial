package com.restaurante.device.offline.entity;

import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.DeviceOfflineReplayOperationStatus;
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
        name = "device_offline_replay_operations",
        uniqueConstraints = @UniqueConstraint(name = "uk_offline_replay_op_operation_id", columnNames = {"tenant_id", "operation_id"}),
        indexes = {
                @Index(name = "idx_offline_replay_ops_tenant_status", columnList = "tenant_id, status, requested_at"),
                @Index(name = "idx_offline_replay_ops_sync_session", columnList = "tenant_id, server_sync_id, requested_at"),
                @Index(name = "idx_offline_replay_ops_operation_id", columnList = "tenant_id, operation_id")
        }
)
@Getter
@Setter
public class DeviceOfflineReplayOperation {

    // TODO Prompt 40.5: cancelamento controlado de replay operations
    // PENDING cancelável; RUNNING com cancelRequested; liberar replay_in_progress com segurança.
    // Não implementar nesta fase.

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sync_session_db_id", nullable = false, updatable = false)
    private DeviceOfflineSyncSession syncSession;

    @Column(name = "server_sync_id", nullable = false, length = 120)
    private String serverSyncId;

    @Column(name = "operation_id", nullable = false, length = 120, updatable = false)
    private String operationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DeviceOfflineReplayOperationStatus status;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private Long requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "last_progress_at")
    private Instant lastProgressAt;

    @Column(name = "last_progress_percent")
    private Integer lastProgressPercent;

    @Column(name = "last_progress_event_at")
    private Instant lastProgressEventAt;

    @Column(name = "last_progress_event_percent")
    private Integer lastProgressEventPercent;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "force", nullable = false)
    private boolean force = false;

    @Column(name = "command_status_filter_json", columnDefinition = "jsonb")
    private String commandStatusFilterJson;

    @Column(name = "command_type_filter_json", columnDefinition = "jsonb")
    private String commandTypeFilterJson;

    @Column(name = "command_ids_json", columnDefinition = "jsonb")
    private String commandIdsJson;

    @Column(name = "total_items", nullable = false)
    private int totalItems = 0;

    @Column(name = "pending_items", nullable = false)
    private int pendingItems = 0;

    @Column(name = "running_items", nullable = false)
    private int runningItems = 0;

    @Column(name = "succeeded_items", nullable = false)
    private int succeededItems = 0;

    @Column(name = "noop_items", nullable = false)
    private int noopItems = 0;

    @Column(name = "blocked_items", nullable = false)
    private int blockedItems = 0;

    @Column(name = "failed_items", nullable = false)
    private int failedItems = 0;

    @Column(name = "progress_percent", nullable = false)
    private int progressPercent = 0;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (requestedAt == null) requestedAt = Instant.now();
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = DeviceOfflineReplayOperationStatus.PENDING;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void clearLock() {
        this.lockedAt = null;
        this.lockedBy = null;
    }
}
