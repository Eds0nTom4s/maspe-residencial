package com.restaurante.model.entity;

import com.restaurante.model.enums.FiscalAutoIssueJobStatus;
import com.restaurante.model.enums.FiscalAutoIssueSource;
import com.restaurante.model.enums.FiscalAutoIssueTriggerType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "fiscal_auto_issue_jobs")
@Getter
@Setter
public class FiscalAutoIssueJob extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_atendimento_id")
    private UnidadeAtendimento unidadeAtendimento;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pagamento_id", nullable = false)
    private Pagamento pagamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_consumo_id")
    private SessaoConsumo sessaoConsumo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caixa_operador_session_id")
    private CaixaOperadorSession caixaOperadorSession;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 80)
    private FiscalAutoIssueSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private FiscalAutoIssueJobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 80)
    private FiscalAutoIssueTriggerType triggerType;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "locked_by", length = 120)
    private String lockedBy;

    @Column(name = "idempotency_key", nullable = false, length = 160)
    private String idempotencyKey;

    @Column(name = "error_code", length = 120)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_document_id")
    private FiscalDocument fiscalDocument;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}

