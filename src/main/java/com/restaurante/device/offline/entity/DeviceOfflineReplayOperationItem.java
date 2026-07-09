package com.restaurante.device.offline.entity;

import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.DeviceOfflineReplayOperationItemStatus;
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
        name = "device_offline_replay_operation_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_offline_replay_item", columnNames = {"tenant_id", "operation_db_id", "device_offline_command_id"}),
        indexes = {
                @Index(name = "idx_offline_replay_items_op_status", columnList = "tenant_id, operation_db_id, item_status, id"),
                @Index(name = "idx_offline_replay_items_cmd", columnList = "tenant_id, device_offline_command_id"),
                @Index(name = "idx_offline_replay_items_tenant_status", columnList = "tenant_id, item_status, id")
        }
)
@Getter
@Setter
public class DeviceOfflineReplayOperationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operation_db_id", nullable = false, updatable = false)
    private DeviceOfflineReplayOperation operation;

    @Column(name = "operation_id", nullable = false, length = 120, updatable = false)
    private String operationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sync_session_db_id", nullable = false, updatable = false)
    private DeviceOfflineSyncSession syncSession;

    @Column(name = "server_sync_id", nullable = false, length = 120, updatable = false)
    private String serverSyncId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_offline_command_id", nullable = false, updatable = false)
    private DeviceOfflineCommand command;

    @Column(name = "client_request_id", nullable = false, length = 120, updatable = false)
    private String clientRequestId;

    @Column(name = "command_type", nullable = false, length = 80, updatable = false)
    private String commandType;

    @Column(name = "previous_status", nullable = false, length = 30, updatable = false)
    private String previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_status", nullable = false, length = 30)
    private DeviceOfflineReplayOperationItemStatus itemStatus;

    @Column(name = "eligibility_status", length = 30)
    private String eligibilityStatus;

    @Column(name = "eligibility_reason", length = 120)
    private String eligibilityReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replay_attempt_id")
    private DeviceOfflineCommandReplayAttempt replayAttempt;

    @Column(name = "result_status", length = 30)
    private String resultStatus;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (itemStatus == null) itemStatus = DeviceOfflineReplayOperationItemStatus.PENDING;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

