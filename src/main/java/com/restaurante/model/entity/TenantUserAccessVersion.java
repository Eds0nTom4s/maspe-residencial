package com.restaurante.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_user_access_versions", indexes = {
        @Index(name = "idx_tuav_tenant_user", columnList = "tenant_id, user_id"),
        @Index(name = "idx_tuav_user", columnList = "user_id"),
        @Index(name = "idx_tuav_permissions_updated_at", columnList = "permissions_updated_at")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TenantUserAccessVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @Column(name = "access_version", nullable = false)
    private Integer accessVersion;

    @NotNull
    @Column(name = "permissions_updated_at", nullable = false)
    private LocalDateTime permissionsUpdatedAt;
}

