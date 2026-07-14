package com.restaurante.financeiro.reconciliation.model;
import com.restaurante.model.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;import lombok.Setter;
@Entity @Table(name="reconciliation_materialization_audits") @Getter @Setter
public class ReconciliationMaterializationAudit extends BaseEntity {
 @Column(name="tenant_id",nullable=false) private Long tenantId; @Column(name="actor_user_id",nullable=false) private Long actorUserId;
 @Column(name="actor_roles",nullable=false,length=500) private String actorRoles; @Column(name="actor_origin",nullable=false,length=50) private String actorOrigin;
 @Column(name="correlation_id",nullable=false,length=100) private String correlationId; @Column(name="idempotency_key",nullable=false,length=100,unique=true) private String idempotencyKey;
 @Column(name="command_fingerprint",nullable=false,length=64) private String commandFingerprint; @Column(nullable=false,length=1000) private String reason;
 @Column(name="dry_run",nullable=false) private boolean dryRun; @Column(name="eligible_count",nullable=false) private long eligibleCount; @Column(name="created_count",nullable=false) private long createdCount;
}
