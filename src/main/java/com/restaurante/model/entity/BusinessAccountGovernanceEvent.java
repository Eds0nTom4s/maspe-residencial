package com.restaurante.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "business_account_governance_events")
public class BusinessAccountGovernanceEvent extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "business_account_id") private BusinessAccount businessAccount;
    @Column(name = "scope_key", nullable = false, length = 120) private String scopeKey;
    @Column(name = "action", nullable = false, length = 50) private String action;
    @Column(name = "idempotency_key", nullable = false, length = 100) private String idempotencyKey;
    @Column(name = "request_fingerprint", nullable = false, length = 64) private String requestFingerprint;
    @Column(name = "actor_user_id") private Long actorUserId;
    @Column(name = "actor_roles", nullable = false, length = 500) private String actorRoles;
    @Column(name = "correlation_id", nullable = false, length = 120) private String correlationId;
    @Column(name = "ip_address", length = 64) private String ipAddress;
    @Column(name = "user_agent", length = 500) private String userAgent;
    @Column(name = "before_state", columnDefinition = "TEXT") private String beforeState;
    @Column(name = "after_state", columnDefinition = "TEXT") private String afterState;
    @Column(name = "result_account_id") private Long resultAccountId;
    @Column(name = "result_member_id") private Long resultMemberId;

    public BusinessAccount getBusinessAccount() { return businessAccount; }
    public void setBusinessAccount(BusinessAccount value) { this.businessAccount = value; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String value) { this.scopeKey = value; }
    public String getAction() { return action; }
    public void setAction(String value) { this.action = value; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { this.idempotencyKey = value; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String value) { this.requestFingerprint = value; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long value) { this.actorUserId = value; }
    public String getActorRoles() { return actorRoles; }
    public void setActorRoles(String value) { this.actorRoles = value; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String value) { this.correlationId = value; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String value) { this.ipAddress = value; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String value) { this.userAgent = value; }
    public String getBeforeState() { return beforeState; }
    public void setBeforeState(String value) { this.beforeState = value; }
    public String getAfterState() { return afterState; }
    public void setAfterState(String value) { this.afterState = value; }
    public Long getResultAccountId() { return resultAccountId; }
    public void setResultAccountId(Long value) { this.resultAccountId = value; }
    public Long getResultMemberId() { return resultMemberId; }
    public void setResultMemberId(Long value) { this.resultMemberId = value; }
}
