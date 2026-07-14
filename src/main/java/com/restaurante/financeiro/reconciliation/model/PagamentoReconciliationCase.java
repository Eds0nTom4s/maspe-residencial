package com.restaurante.financeiro.reconciliation.model;

import com.restaurante.model.entity.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagamento_reconciliation_cases")
@Getter @Setter
public class PagamentoReconciliationCase extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name="tenant_id", nullable=false)
    private Tenant tenant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name="pagamento_gateway_id", nullable=false)
    private Pagamento pagamento;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name="pedido_id") private Pedido pedido;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=50) private ReconciliationCaseStatus status;
    @Enumerated(EnumType.STRING) @Column(length=60) private ReconciliationCaseClassification classification;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name="assigned_to_user_id") private User assignedTo;
    @Column(name="opened_at", nullable=false) private LocalDateTime openedAt;
    @Column(name="opened_by", nullable=false, length=100) private String openedBy;
    @Column(name="resolved_at") private LocalDateTime resolvedAt;
    @Column(name="resolved_by", length=100) private String resolvedBy;
    @Column(length=60) private String resolution;
    @Column(name="resolution_reason", length=1000) private String resolutionReason;
    @Column(name="created_from_reconciliation_status", nullable=false, length=50) private String createdFromReconciliationStatus;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private ReconciliationCaseOrigin origin;
    @Column(name="remote_status_snapshot", length=50) private String remoteStatusSnapshot;
    @Column(name="local_status_snapshot", length=50) private String localStatusSnapshot;
    @Column(name="remote_reference_snapshot", length=100) private String remoteReferenceSnapshot;
    @Column(name="response_fingerprint_snapshot", length=64) private String responseFingerprintSnapshot;
    @Column(nullable=false) private boolean active = true;
}
