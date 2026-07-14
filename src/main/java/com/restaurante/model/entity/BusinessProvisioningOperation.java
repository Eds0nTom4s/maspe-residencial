package com.restaurante.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "business_provisioning_operations")
public class BusinessProvisioningOperation extends BaseEntity {
    @Column(name = "operation_id", nullable = false, unique = true, length = 36) private String operationId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "business_account_id", nullable = false) private BusinessAccount businessAccount;
    @Column(name = "actor_user_id") private Long actorUserId;
    @Column(name = "idempotency_key", nullable = false, length = 100) private String idempotencyKey;
    @Column(name = "request_fingerprint", nullable = false, length = 64) private String requestFingerprint;
    @Column(name = "preview_id", nullable = false, length = 36) private String previewId;
    @Column(name = "status", nullable = false, length = 30) private String status;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "tenant_id") private Tenant tenant;
    @Column(name = "started_at", nullable = false) private LocalDateTime startedAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Column(name = "error_code", length = 120) private String errorCode;
    @Column(name = "error_message", length = 500) private String errorMessage;
    @Column(name = "result_json", columnDefinition = "TEXT") private String resultJson;
    @Column(name = "correlation_id", nullable = false, length = 120) private String correlationId;
    @Column(name = "actor_roles", nullable = false, length = 500) private String actorRoles;
    @Column(name = "ip_address", length = 64) private String ipAddress;
    @Column(name = "user_agent", length = 500) private String userAgent;

    public String getOperationId() { return operationId; }
    public void setOperationId(String v) { operationId = v; }
    public BusinessAccount getBusinessAccount() { return businessAccount; }
    public void setBusinessAccount(BusinessAccount v) { businessAccount = v; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long v) { actorUserId = v; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String v) { idempotencyKey = v; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String v) { requestFingerprint = v; }
    public String getPreviewId() { return previewId; }
    public void setPreviewId(String v) { previewId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant v) { tenant = v; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime v) { startedAt = v; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime v) { completedAt = v; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String v) { errorCode = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { errorMessage = v; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String v) { resultJson = v; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String v) { correlationId = v; }
    public String getActorRoles() { return actorRoles; }
    public void setActorRoles(String v) { actorRoles = v; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String v) { ipAddress = v; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String v) { userAgent = v; }
}
