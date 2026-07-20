package com.restaurante.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "onboarding_command_records")
public class OnboardingCommandRecord extends BaseEntity {
    @Column(name = "scope_key", nullable = false, length = 120) private String scopeKey;
    @Column(name = "action", nullable = false, length = 50) private String action;
    @Column(name = "idempotency_key", nullable = false, length = 100) private String idempotencyKey;
    @Column(name = "request_fingerprint", nullable = false, length = 64) private String requestFingerprint;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "onboarding_request_id", nullable = false) private OnboardingRequest onboardingRequest;
    @Column(name = "actor_user_id") private Long actorUserId;
    @Column(name = "actor_roles", nullable = false, length = 500) private String actorRoles;
    @Column(name = "correlation_id", nullable = false, length = 120) private String correlationId;
    @Column(name = "ip_address", length = 64) private String ipAddress;
    @Column(name = "user_agent", length = 500) private String userAgent;
    @Column(name = "before_state", columnDefinition = "TEXT") private String beforeState;
    @Column(name = "after_state", columnDefinition = "TEXT") private String afterState;
    @Column(name = "reason", length = 500) private String reason;
    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT") private String resultJson;
    @Column(name = "result_account_id") private Long resultAccountId;
    @Column(name = "result_operation_id", length = 36) private String resultOperationId;
    @Column(name = "result_tenant_id") private Long resultTenantId;

    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String v) { scopeKey = v; }
    public String getAction() { return action; }
    public void setAction(String v) { action = v; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String v) { idempotencyKey = v; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String v) { requestFingerprint = v; }
    public OnboardingRequest getOnboardingRequest() { return onboardingRequest; }
    public void setOnboardingRequest(OnboardingRequest v) { onboardingRequest = v; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long v) { actorUserId = v; }
    public String getActorRoles() { return actorRoles; }
    public void setActorRoles(String v) { actorRoles = v; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String v) { correlationId = v; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String v) { ipAddress = v; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String v) { userAgent = v; }
    public String getBeforeState() { return beforeState; }
    public void setBeforeState(String v) { beforeState = v; }
    public String getAfterState() { return afterState; }
    public void setAfterState(String v) { afterState = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { reason = v; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String v) { resultJson = v; }
    public Long getResultAccountId() { return resultAccountId; }
    public void setResultAccountId(Long v) { resultAccountId = v; }
    public String getResultOperationId() { return resultOperationId; }
    public void setResultOperationId(String v) { resultOperationId = v; }
    public Long getResultTenantId() { return resultTenantId; }
    public void setResultTenantId(Long v) { resultTenantId = v; }
}
