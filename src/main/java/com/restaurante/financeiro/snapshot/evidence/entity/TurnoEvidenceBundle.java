package com.restaurante.financeiro.snapshot.evidence.entity;

import com.restaurante.model.entity.BaseEntity;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.EvidenceBundleStatus;
import com.restaurante.model.enums.EvidenceBundleType;
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
@Table(name = "turno_evidence_bundles", indexes = {
        @Index(name = "idx_turno_ev_bundle_turno", columnList = "tenant_id, turno_id"),
        @Index(name = "idx_turno_ev_bundle_generated_at", columnList = "tenant_id, generated_at"),
        @Index(name = "idx_turno_ev_bundle_hash", columnList = "tenant_id, bundle_hash"),
        @Index(name = "idx_turno_ev_bundle_status", columnList = "tenant_id, status")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TurnoEvidenceBundle extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turno_id", nullable = false)
    private TurnoOperacional turno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instituicao_id")
    private Instituicao instituicao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_atendimento_id")
    private UnidadeAtendimento unidadeAtendimento;

    @NotNull
    @Column(name = "bundle_version", nullable = false, length = 30)
    private String bundleVersion;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "bundle_type", nullable = false, length = 50)
    private EvidenceBundleType bundleType = EvidenceBundleType.FINANCEIRO_TURNO_SNAPSHOT_EVIDENCE;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EvidenceBundleStatus status = EvidenceBundleStatus.ACTIVE;

    @NotNull
    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @NotNull
    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_by_user_id")
    private User generatedByUser;

    @Column(name = "generated_by_actor_type", length = 50)
    private String generatedByActorType;

    @Column(name = "source_endpoint", length = 150)
    private String sourceEndpoint;

    @NotNull
    @Column(name = "canonicalization_version", nullable = false, length = 20)
    private String canonicalizationVersion;

    @NotNull
    @Column(name = "hash_algorithm", nullable = false, length = 50)
    private String hashAlgorithm;

    @NotNull
    @Column(name = "bundle_hash", nullable = false, length = 128)
    private String bundleHash;

    @NotNull
    @Column(name = "signature_algorithm", nullable = false, length = 50)
    private String signatureAlgorithm;

    @NotNull
    @Column(name = "bundle_signature", nullable = false, columnDefinition = "text")
    private String bundleSignature;

    @NotNull
    @Column(name = "signature_key_id", nullable = false, length = 100)
    private String signatureKeyId;

    @NotNull
    @Column(name = "signature_generated_at", nullable = false)
    private LocalDateTime signatureGeneratedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_bundle_id")
    private TurnoEvidenceBundle previousBundle;

    @Column(name = "previous_bundle_hash", length = 128)
    private String previousBundleHash;

    @NotNull
    @Column(name = "chain_hash", nullable = false, length = 128)
    private String chainHash;

    @NotNull
    @Column(name = "chain_signature", nullable = false, columnDefinition = "text")
    private String chainSignature;

    @NotNull
    @Column(name = "chain_signature_key_id", nullable = false, length = 100)
    private String chainSignatureKeyId;

    @NotNull
    @Column(name = "chain_signature_generated_at", nullable = false)
    private LocalDateTime chainSignatureGeneratedAt;

    @Column(name = "retention_until")
    private LocalDateTime retentionUntil;

    @NotNull
    @Column(name = "worm_locked", nullable = false)
    private boolean wormLocked = true;

    @NotNull
    @Column(name = "bundle_json", nullable = false, columnDefinition = "jsonb")
    private String bundleJson;

    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson;
}

