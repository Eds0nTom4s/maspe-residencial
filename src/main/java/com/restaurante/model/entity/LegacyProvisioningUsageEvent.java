package com.restaurante.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "legacy_provisioning_usage_events")
public class LegacyProvisioningUsageEvent extends BaseEntity {
    @Column(name = "endpoint", nullable = false, length = 180) private String endpoint;
    @Column(name = "actor_user_id") private Long actorUserId;
    @Column(name = "actor_roles", nullable = false, length = 500) private String actorRoles;
    @Column(name = "correlation_id", nullable = false, length = 120) private String correlationId;
    @Column(name = "ip_address", length = 64) private String ipAddress;
    @Column(name = "user_agent", length = 500) private String userAgent;
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String v) { endpoint = v; }
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
}
