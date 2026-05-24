package com.restaurante.model.entity;

import com.restaurante.model.enums.FiscalDocumentSequenceStatus;
import com.restaurante.model.enums.FiscalDocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fiscal_document_sequences", indexes = {
        @Index(name = "uq_fiscal_seq_key", columnList = "tenant_id, unidade_atendimento_id, document_type, series, seq_year", unique = true)
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class FiscalDocumentSequence extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_atendimento_id")
    private UnidadeAtendimento unidadeAtendimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private FiscalDocumentType documentType;

    @Column(name = "series", nullable = false, length = 20)
    private String series;

    @Column(name = "seq_year", nullable = false)
    private Integer year;

    @Column(name = "current_number", nullable = false)
    private Long currentNumber = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private FiscalDocumentSequenceStatus status = FiscalDocumentSequenceStatus.ACTIVE;
}
