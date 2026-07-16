package com.restaurante.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "business_provisioning_previews")
public class BusinessProvisioningPreview extends BaseEntity {
    @Column(name = "preview_id", nullable = false, unique = true, length = 36) private String previewId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "business_account_id", nullable = false) private BusinessAccount businessAccount;
    @Column(name = "actor_user_id") private Long actorUserId;
    @Column(name = "idempotency_key", nullable = false, length = 100) private String idempotencyKey;
    @Column(name = "request_fingerprint", nullable = false, length = 64) private String requestFingerprint;
    @Column(name = "contract_version", nullable = false, length = 40) private String contractVersion;
    @Column(name = "template_code", nullable = false, length = 60) private String templateCode;
    @Column(name = "template_version", nullable = false) private Integer templateVersion;
    @Column(name = "plano_codigo", nullable = false, length = 30) private String planoCodigo;
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT") private String payloadJson;
    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT") private String resultJson;
    @Column(name = "status", nullable = false, length = 20) private String status;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "consumed_at") private LocalDateTime consumedAt;
    @Column(name = "correlation_id", nullable = false, length = 120) private String correlationId;
    @Column(name = "actor_roles", nullable = false, length = 500) private String actorRoles;
    @Column(name = "ip_address", length = 64) private String ipAddress;
    @Column(name = "user_agent", length = 500) private String userAgent;

    public String getPreviewId() { return previewId; }
    public void setPreviewId(String v) { previewId = v; }
    public BusinessAccount getBusinessAccount() { return businessAccount; }
    public void setBusinessAccount(BusinessAccount v) { businessAccount = v; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long v) { actorUserId = v; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String v) { idempotencyKey = v; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String v) { requestFingerprint = v; }
    public String getContractVersion() { return contractVersion; }
    public void setContractVersion(String v) { contractVersion = v; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String v) { templateCode = v; }
    public Integer getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(Integer v) { templateVersion = v; }
    public String getPlanoCodigo() { return planoCodigo; }
    public void setPlanoCodigo(String v) { planoCodigo = v; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String v) { payloadJson = v; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String v) { resultJson = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime v) { expiresAt = v; }
    public LocalDateTime getConsumedAt() { return consumedAt; }
    public void setConsumedAt(LocalDateTime v) { consumedAt = v; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String v) { correlationId = v; }
    public String getActorRoles() { return actorRoles; }
    public void setActorRoles(String v) { actorRoles = v; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String v) { ipAddress = v; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String v) { userAgent = v; }
}
