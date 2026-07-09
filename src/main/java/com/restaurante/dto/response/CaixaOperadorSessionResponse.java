package com.restaurante.dto.response;

import com.restaurante.model.enums.CaixaOperadorSessionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CaixaOperadorSessionResponse {
    private Long id;
    private CaixaOperadorSessionStatus status;

    private Long tenantId;
    private Long instituicaoId;
    private Long unidadeAtendimentoId;
    private Long turnoOperacionalId;
    private Long deviceId;
    private Long operadorUserId;

    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private LocalDateTime reviewedAt;

    private BigDecimal expectedCashAmount;
    private BigDecimal declaredCashAmount;
    private BigDecimal cashDifferenceAmount;

    private BigDecimal expectedTpaAmount;
    private BigDecimal declaredTpaAmount;
    private BigDecimal tpaDifferenceAmount;

    private BigDecimal expectedManualTotalAmount;
    private BigDecimal declaredManualTotalAmount;
    private BigDecimal manualDifferenceAmount;

    private String currency;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public CaixaOperadorSessionStatus getStatus() { return status; }
    public void setStatus(CaixaOperadorSessionStatus status) { this.status = status; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getInstituicaoId() { return instituicaoId; }
    public void setInstituicaoId(Long instituicaoId) { this.instituicaoId = instituicaoId; }
    public Long getUnidadeAtendimentoId() { return unidadeAtendimentoId; }
    public void setUnidadeAtendimentoId(Long unidadeAtendimentoId) { this.unidadeAtendimentoId = unidadeAtendimentoId; }
    public Long getTurnoOperacionalId() { return turnoOperacionalId; }
    public void setTurnoOperacionalId(Long turnoOperacionalId) { this.turnoOperacionalId = turnoOperacionalId; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public Long getOperadorUserId() { return operadorUserId; }
    public void setOperadorUserId(Long operadorUserId) { this.operadorUserId = operadorUserId; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime openedAt) { this.openedAt = openedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public BigDecimal getExpectedCashAmount() { return expectedCashAmount; }
    public void setExpectedCashAmount(BigDecimal expectedCashAmount) { this.expectedCashAmount = expectedCashAmount; }
    public BigDecimal getDeclaredCashAmount() { return declaredCashAmount; }
    public void setDeclaredCashAmount(BigDecimal declaredCashAmount) { this.declaredCashAmount = declaredCashAmount; }
    public BigDecimal getCashDifferenceAmount() { return cashDifferenceAmount; }
    public void setCashDifferenceAmount(BigDecimal cashDifferenceAmount) { this.cashDifferenceAmount = cashDifferenceAmount; }
    public BigDecimal getExpectedTpaAmount() { return expectedTpaAmount; }
    public void setExpectedTpaAmount(BigDecimal expectedTpaAmount) { this.expectedTpaAmount = expectedTpaAmount; }
    public BigDecimal getDeclaredTpaAmount() { return declaredTpaAmount; }
    public void setDeclaredTpaAmount(BigDecimal declaredTpaAmount) { this.declaredTpaAmount = declaredTpaAmount; }
    public BigDecimal getTpaDifferenceAmount() { return tpaDifferenceAmount; }
    public void setTpaDifferenceAmount(BigDecimal tpaDifferenceAmount) { this.tpaDifferenceAmount = tpaDifferenceAmount; }
    public BigDecimal getExpectedManualTotalAmount() { return expectedManualTotalAmount; }
    public void setExpectedManualTotalAmount(BigDecimal expectedManualTotalAmount) { this.expectedManualTotalAmount = expectedManualTotalAmount; }
    public BigDecimal getDeclaredManualTotalAmount() { return declaredManualTotalAmount; }
    public void setDeclaredManualTotalAmount(BigDecimal declaredManualTotalAmount) { this.declaredManualTotalAmount = declaredManualTotalAmount; }
    public BigDecimal getManualDifferenceAmount() { return manualDifferenceAmount; }
    public void setManualDifferenceAmount(BigDecimal manualDifferenceAmount) { this.manualDifferenceAmount = manualDifferenceAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}

