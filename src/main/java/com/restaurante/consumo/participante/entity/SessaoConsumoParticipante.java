package com.restaurante.consumo.participante.entity;

import com.restaurante.consumo.identificacao.entity.ClienteConsumo;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.SessaoParticipanteRole;
import com.restaurante.model.enums.SessaoParticipanteStatus;
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
        name = "sessao_consumo_participantes",
        uniqueConstraints = @UniqueConstraint(name = "uk_sessao_participante", columnNames = {"tenant_id", "sessao_consumo_id", "cliente_consumo_id"}),
        indexes = {
                @Index(name = "idx_sessao_participantes_tenant_sessao", columnList = "tenant_id, sessao_consumo_id, status"),
                @Index(name = "idx_sessao_participantes_tenant_cliente", columnList = "tenant_id, cliente_consumo_id, status"),
                @Index(name = "idx_sessao_participantes_tenant_status", columnList = "tenant_id, status"),
                @Index(name = "idx_sessao_participantes_telefone", columnList = "tenant_id, telefone_normalizado")
        }
)
@Getter
@Setter
public class SessaoConsumoParticipante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sessao_consumo_id", nullable = false, updatable = false)
    private SessaoConsumo sessaoConsumo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_consumo_id", nullable = false, updatable = false)
    private ClienteConsumo clienteConsumo;

    @Column(name = "telefone_normalizado", nullable = false, length = 30)
    private String telefoneNormalizado;

    @Column(name = "nome_exibicao", length = 120)
    private String nomeExibicao;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private SessaoParticipanteRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SessaoParticipanteStatus status;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removed_by_device_id")
    private Long removedByDeviceId;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "blocked_at")
    private Instant blockedAt;

    @Column(name = "blocked_by_device_id")
    private Long blockedByDeviceId;

    @Column(name = "restored_by_device_id")
    private Long restoredByDeviceId;

    @Column(name = "promoted_at")
    private Instant promotedAt;

    @Column(name = "demoted_at")
    private Instant demotedAt;

    @Column(name = "promoted_by_device_id")
    private Long promotedByDeviceId;

    @Column(name = "demoted_by_device_id")
    private Long demotedByDeviceId;

    @Column(name = "invited_by_participante_id")
    private Long invitedByParticipanteId;

    @Column(name = "invited_by_device_id")
    private Long invitedByDeviceId;

    @Column(name = "invited_at")
    private Instant invitedAt;

    @Column(name = "invitation_expires_at")
    private Instant invitationExpiresAt;

    @Column(name = "approval_requested_at")
    private Instant approvalRequestedAt;

    @Column(name = "approved_by_participante_id")
    private Long approvedByParticipanteId;

    @Column(name = "approved_by_device_id")
    private Long approvedByDeviceId;

    @Column(name = "rejected_by_participante_id")
    private Long rejectedByParticipanteId;

    @Column(name = "rejected_by_device_id")
    private Long rejectedByDeviceId;

    @Column(name = "approval_decided_at")
    private Instant approvalDecidedAt;

    @Column(name = "approval_reason", length = 255)
    private String approvalReason;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @Column(name = "entry_policy_snapshot", length = 50)
    private String entryPolicySnapshot;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by_participante_id")
    private Long cancelledByParticipanteId;

    @Column(name = "cancelled_by_device_id")
    private Long cancelledByDeviceId;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(name = "resend_count", nullable = false)
    private int resendCount = 0;

    @Column(name = "last_resend_at")
    private Instant lastResendAt;

    @Column(name = "last_reminder_at")
    private Instant lastReminderAt;

    @Column(name = "expiration_reason", length = 120)
    private String expirationReason;

    @Column(name = "cleanup_batch_id", length = 120)
    private String cleanupBatchId;

    @Column(name = "role_changed_reason", length = 255)
    private String roleChangedReason;

    @Column(name = "status_changed_reason", length = 255)
    private String statusChangedReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by_cliente_consumo_id")
    private ClienteConsumo addedByClienteConsumo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by_device_id")
    private DispositivoOperacional addedByDevice;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = SessaoParticipanteStatus.PENDING_OTP;
        if (role == null) role = SessaoParticipanteRole.MEMBER;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isActive() {
        return status == SessaoParticipanteStatus.ACTIVE;
    }
}
