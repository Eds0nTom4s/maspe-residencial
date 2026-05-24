package com.restaurante.dto.response;

import com.restaurante.model.enums.CaixaOperadorDivergencePaymentMethod;
import com.restaurante.model.enums.CaixaOperadorDivergenceReasonCategory;
import com.restaurante.model.enums.CaixaOperadorDivergenceSeverity;
import com.restaurante.model.enums.CaixaOperadorDivergenceStatus;
import com.restaurante.model.enums.CaixaOperadorDivergenceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CaixaOperadorDivergenceResponse {
    private Long id;
    private Long tenantId;
    private Long unidadeAtendimentoId;
    private Long turnoOperacionalId;
    private Long caixaOperadorSessionId;
    private Long deviceId;
    private Long operadorUserId;

    private CaixaOperadorDivergenceStatus status;
    private CaixaOperadorDivergenceType type;
    private CaixaOperadorDivergenceSeverity severity;
    private CaixaOperadorDivergencePaymentMethod paymentMethod;

    private BigDecimal expectedAmount;
    private BigDecimal declaredAmount;
    private BigDecimal differenceAmount;
    private BigDecimal absoluteDifferenceAmount;

    private CaixaOperadorDivergenceReasonCategory reasonCategory;
    private String description;

    private Long submittedByUserId;
    private LocalDateTime submittedAt;

    private Long reviewedByUserId;
    private LocalDateTime reviewedAt;
    private String reviewNotes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getUnidadeAtendimentoId() { return unidadeAtendimentoId; }
    public void setUnidadeAtendimentoId(Long unidadeAtendimentoId) { this.unidadeAtendimentoId = unidadeAtendimentoId; }
    public Long getTurnoOperacionalId() { return turnoOperacionalId; }
    public void setTurnoOperacionalId(Long turnoOperacionalId) { this.turnoOperacionalId = turnoOperacionalId; }
    public Long getCaixaOperadorSessionId() { return caixaOperadorSessionId; }
    public void setCaixaOperadorSessionId(Long caixaOperadorSessionId) { this.caixaOperadorSessionId = caixaOperadorSessionId; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public Long getOperadorUserId() { return operadorUserId; }
    public void setOperadorUserId(Long operadorUserId) { this.operadorUserId = operadorUserId; }
    public CaixaOperadorDivergenceStatus getStatus() { return status; }
    public void setStatus(CaixaOperadorDivergenceStatus status) { this.status = status; }
    public CaixaOperadorDivergenceType getType() { return type; }
    public void setType(CaixaOperadorDivergenceType type) { this.type = type; }
    public CaixaOperadorDivergenceSeverity getSeverity() { return severity; }
    public void setSeverity(CaixaOperadorDivergenceSeverity severity) { this.severity = severity; }
    public CaixaOperadorDivergencePaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(CaixaOperadorDivergencePaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public BigDecimal getExpectedAmount() { return expectedAmount; }
    public void setExpectedAmount(BigDecimal expectedAmount) { this.expectedAmount = expectedAmount; }
    public BigDecimal getDeclaredAmount() { return declaredAmount; }
    public void setDeclaredAmount(BigDecimal declaredAmount) { this.declaredAmount = declaredAmount; }
    public BigDecimal getDifferenceAmount() { return differenceAmount; }
    public void setDifferenceAmount(BigDecimal differenceAmount) { this.differenceAmount = differenceAmount; }
    public BigDecimal getAbsoluteDifferenceAmount() { return absoluteDifferenceAmount; }
    public void setAbsoluteDifferenceAmount(BigDecimal absoluteDifferenceAmount) { this.absoluteDifferenceAmount = absoluteDifferenceAmount; }
    public CaixaOperadorDivergenceReasonCategory getReasonCategory() { return reasonCategory; }
    public void setReasonCategory(CaixaOperadorDivergenceReasonCategory reasonCategory) { this.reasonCategory = reasonCategory; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getSubmittedByUserId() { return submittedByUserId; }
    public void setSubmittedByUserId(Long submittedByUserId) { this.submittedByUserId = submittedByUserId; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public Long getReviewedByUserId() { return reviewedByUserId; }
    public void setReviewedByUserId(Long reviewedByUserId) { this.reviewedByUserId = reviewedByUserId; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }
}

