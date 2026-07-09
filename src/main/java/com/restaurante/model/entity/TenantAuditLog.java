package com.restaurante.model.entity;

import com.restaurante.model.enums.TenantAuditAction;
import com.restaurante.model.enums.TenantAuditStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tenant_audit_logs", indexes = {
        @Index(name = "idx_tenant_audit_logs_tenant", columnList = "tenant_id"),
        @Index(name = "idx_tenant_audit_logs_actor", columnList = "actor_user_id"),
        @Index(name = "idx_tenant_audit_logs_target", columnList = "target_user_id"),
        @Index(name = "idx_tenant_audit_logs_action", columnList = "action"),
        @Index(name = "idx_tenant_audit_logs_created_at", columnList = "created_at"),
        @Index(name = "idx_tenant_audit_logs_tenant_created_at", columnList = "tenant_id, created_at")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TenantAuditLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @NotNull
    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @NotBlank
    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 60)
    private TenantAuditAction action;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TenantAuditStatus status;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;
}

