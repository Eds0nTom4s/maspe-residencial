package com.restaurante.financeiro.reconciliation.model;

import com.restaurante.model.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name="pagamento_reconciliation_case_events")
@Getter @Setter
public class PagamentoReconciliationCaseEvent extends BaseEntity {
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="case_id", nullable=false) private PagamentoReconciliationCase reconciliationCase;
    @Column(name="tenant_id", nullable=false) private Long tenantId;
    @Column(name="pagamento_id", nullable=false) private Long pagamentoId;
    @Column(name="pedido_id") private Long pedidoId;
    @Column(name="actor_user_id") private Long actorUserId;
    @Column(name="actor_roles", nullable=false, length=500) private String actorRoles;
    @Column(name="actor_origin", nullable=false, length=50) private String actorOrigin;
    @Column(length=64) private String ip;
    @Column(name="user_agent", length=255) private String userAgent;
    @Column(name="correlation_id", nullable=false, length=100) private String correlationId;
    @Column(name="idempotency_key", length=100) private String idempotencyKey;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=40) private ReconciliationCaseAction action;
    @Column(name="before_state", columnDefinition="text") private String beforeState;
    @Column(name="after_state", columnDefinition="text") private String afterState;
    @Column(nullable=false, length=1000) private String reason;
    @Enumerated(EnumType.STRING) @Column(name="note_type", length=30) private ReconciliationNoteType noteType;
    @Enumerated(EnumType.STRING) @Column(name="note_visibility", length=30) private ReconciliationNoteVisibility noteVisibility;
}
