package com.restaurante.model.entity;

import com.restaurante.model.enums.FiscalDocumentSource;
import com.restaurante.model.enums.FiscalDocumentStatus;
import com.restaurante.model.enums.FiscalDocumentType;
import com.restaurante.model.enums.FiscalRegime;
import com.restaurante.model.enums.FiscalCorrectionSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "fiscal_documents", indexes = {
        @Index(name = "uq_fiscal_doc_number", columnList = "tenant_id, series, document_number", unique = true),
        @Index(name = "idx_fiscal_doc_tenant_issued_at", columnList = "tenant_id, issued_at"),
        @Index(name = "idx_fiscal_doc_turno", columnList = "tenant_id, turno_operacional_id"),
        @Index(name = "idx_fiscal_doc_pedido", columnList = "tenant_id, pedido_id"),
        @Index(name = "idx_fiscal_doc_status", columnList = "tenant_id, status")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class FiscalDocument extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instituicao_id")
    private Instituicao instituicao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_atendimento_id")
    private UnidadeAtendimento unidadeAtendimento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turno_operacional_id")
    private TurnoOperacional turnoOperacional;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_consumo_id")
    private SessaoConsumo sessaoConsumo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pagamento_id")
    private Pagamento pagamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caixa_operador_session_id")
    private CaixaOperadorSession caixaOperadorSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_fiscal_document_id")
    private FiscalDocument originalFiscalDocument;

    @Column(name = "correction_reason", columnDefinition = "text")
    private String correctionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "correction_source", length = 80)
    private FiscalCorrectionSource correctionSource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caixa_operador_adjustment_id")
    private CaixaOperadorAdjustment caixaOperadorAdjustment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_adjustment_assessment_id")
    private FiscalAdjustmentAssessment fiscalAdjustmentAssessment;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 50)
    private FiscalDocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private FiscalDocumentStatus status = FiscalDocumentStatus.ISSUED;

    @Enumerated(EnumType.STRING)
    @Column(name = "fiscal_regime", nullable = false, length = 40)
    private FiscalRegime fiscalRegime;

    @Column(name = "document_number", nullable = false, length = 60)
    private String documentNumber;

    @Column(name = "series", nullable = false, length = 20)
    private String series;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "customer_name", length = 255)
    private String customerName;

    @Column(name = "customer_taxpayer_number", length = 40)
    private String customerTaxpayerNumber;

    @Column(name = "subtotal_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotalAmount = BigDecimal.ZERO;

    @Column(name = "taxable_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxableAmount = BigDecimal.ZERO;

    @Column(name = "exempt_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal exemptAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "AOA";

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private FiscalDocumentSource source = FiscalDocumentSource.SYSTEM;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operational_device_id")
    private DispositivoOperacional operationalDevice;

    @OneToMany(mappedBy = "fiscalDocument", fetch = FetchType.LAZY)
    private List<FiscalDocumentLine> lines = new ArrayList<>();
}
