package com.restaurante.consumo.participante.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Prompt 41.4 — Registo de execuções do SessaoParticipanteExpirationJob.
 * Permite health check e observabilidade do job de expiração de participantes.
 */
@Entity
@Table(
        name = "sessao_participante_lifecycle_job_runs",
        indexes = {
                @Index(name = "idx_job_runs_job_name_started",
                        columnList = "job_name, started_at"),
                @Index(name = "idx_job_runs_created_at",
                        columnList = "created_at")
        }
)
@Getter
@Setter
public class SessaoParticipanteLifecycleJobRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** null = job global/multi-tenant */
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "job_name", nullable = false, length = 100)
    private String jobName;

    @Column(name = "batch_id", nullable = false, length = 120, unique = true)
    private String batchId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** RUNNING | SUCCESS | FAILED */
    @Column(name = "status", nullable = false, length = 30)
    private String status = "RUNNING";

    @Column(name = "scanned_count", nullable = false)
    private int scannedCount = 0;

    @Column(name = "expired_count", nullable = false)
    private int expiredCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = "RUNNING";
    }
}
