package com.restaurante.dto.response;

import com.restaurante.model.enums.CaixaOperadorAdjustmentDirection;
import com.restaurante.model.enums.CaixaOperadorAdjustmentStatus;
import com.restaurante.model.enums.CaixaOperadorAdjustmentType;
import com.restaurante.model.enums.CaixaOperadorDivergencePaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CaixaOperadorAdjustmentResponse {
    private Long id;
    private Long tenantId;
    private Long divergenceId;
    private Long caixaOperadorSessionId;
    private CaixaOperadorAdjustmentType adjustmentType;
    private CaixaOperadorDivergencePaymentMethod paymentMethod;
    private BigDecimal amount;
    private CaixaOperadorAdjustmentDirection direction;
    private CaixaOperadorAdjustmentStatus status;
    private Long approvedByUserId;
    private LocalDateTime approvedAt;
    private String reason;
    private String evidenceReference;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getDivergenceId() { return divergenceId; }
    public void setDivergenceId(Long divergenceId) { this.divergenceId = divergenceId; }
    public Long getCaixaOperadorSessionId() { return caixaOperadorSessionId; }
    public void setCaixaOperadorSessionId(Long caixaOperadorSessionId) { this.caixaOperadorSessionId = caixaOperadorSessionId; }
    public CaixaOperadorAdjustmentType getAdjustmentType() { return adjustmentType; }
    public void setAdjustmentType(CaixaOperadorAdjustmentType adjustmentType) { this.adjustmentType = adjustmentType; }
    public CaixaOperadorDivergencePaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(CaixaOperadorDivergencePaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public CaixaOperadorAdjustmentDirection getDirection() { return direction; }
    public void setDirection(CaixaOperadorAdjustmentDirection direction) { this.direction = direction; }
    public CaixaOperadorAdjustmentStatus getStatus() { return status; }
    public void setStatus(CaixaOperadorAdjustmentStatus status) { this.status = status; }
    public Long getApprovedByUserId() { return approvedByUserId; }
    public void setApprovedByUserId(Long approvedByUserId) { this.approvedByUserId = approvedByUserId; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getEvidenceReference() { return evidenceReference; }
    public void setEvidenceReference(String evidenceReference) { this.evidenceReference = evidenceReference; }
}

