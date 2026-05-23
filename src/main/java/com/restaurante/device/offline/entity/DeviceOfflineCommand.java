package com.restaurante.device.offline.entity;

import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.DeviceOfflineCommandStatus;
import com.restaurante.model.enums.DeviceOfflineCommandType;
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
        name = "device_offline_commands",
        uniqueConstraints = @UniqueConstraint(name = "uk_device_offline_cmd", columnNames = {"tenant_id", "dispositivo_operacional_id", "client_request_id"}),
        indexes = {
                @Index(name = "idx_device_offline_cmd_tenant_device", columnList = "tenant_id, dispositivo_operacional_id"),
                @Index(name = "idx_device_offline_cmd_status", columnList = "tenant_id, status"),
                @Index(name = "idx_device_offline_cmd_received_at", columnList = "tenant_id, received_at"),
                @Index(name = "idx_device_offline_cmd_client_request", columnList = "tenant_id, dispositivo_operacional_id, client_request_id")
        }
)
@Getter
@Setter
public class DeviceOfflineCommand {

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

    @Column(name = "client_request_id", nullable = false, length = 120)
    private String clientRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false, length = 80)
    private DeviceOfflineCommandType commandType;

    @Column(name = "command_version", nullable = false, length = 20)
    private String commandVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DeviceOfflineCommandStatus status;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "local_created_at")
    private Instant localCreatedAt;

    @Column(name = "local_sequence")
    private Long localSequence;

    @Column(name = "payload_hash", nullable = false, length = 128)
    private String payloadHash;

    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "payload_size_bytes")
    private Integer payloadSizeBytes;

    @Column(name = "command_index")
    private Integer commandIndex;

    @Column(name = "depends_on_client_request_id", length = 120)
    private String dependsOnClientRequestId;

    @Column(name = "dependency_status", length = 30)
    private String dependencyStatus;

    @Column(name = "result_json", columnDefinition = "jsonb")
    private String resultJson;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "conflict_code", length = 100)
    private String conflictCode;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "idempotency_scope", nullable = false, length = 150)
    private String idempotencyScope;

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
        if (receivedAt == null) receivedAt = Instant.now();
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = DeviceOfflineCommandStatus.RECEIVED;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
