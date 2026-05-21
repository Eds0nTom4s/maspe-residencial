package com.restaurante.financeiro.snapshot.evidence.entity;

import com.restaurante.model.entity.BaseEntity;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.EvidenceBundleAccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "turno_evidence_bundle_access_logs", indexes = {
        @Index(name = "idx_turno_ev_access_bundle", columnList = "tenant_id, bundle_id"),
        @Index(name = "idx_turno_ev_access_turno", columnList = "tenant_id, turno_id"),
        @Index(name = "idx_turno_ev_access_at", columnList = "tenant_id, accessed_at")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TurnoEvidenceBundleAccessLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bundle_id", nullable = false)
    private TurnoEvidenceBundle bundle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turno_id", nullable = false)
    private TurnoOperacional turno;

    @NotNull
    @Column(name = "accessed_at", nullable = false)
    private LocalDateTime accessedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accessed_by_user_id")
    private User accessedByUser;

    @Column(name = "actor_type", length = 50)
    private String actorType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", nullable = false, length = 50)
    private EvidenceBundleAccessType accessType;

    @Column(name = "source_ip", length = 100)
    private String sourceIp;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "verification_result", length = 50)
    private String verificationResult;

    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson;
}

