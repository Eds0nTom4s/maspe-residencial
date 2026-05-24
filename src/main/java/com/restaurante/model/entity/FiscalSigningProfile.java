package com.restaurante.model.entity;

import com.restaurante.model.enums.FiscalKeyProvider;
import com.restaurante.model.enums.FiscalSigningProfileStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "fiscal_signing_profiles", indexes = {
        @Index(name = "idx_fsp_tenant_status", columnList = "tenant_id, status")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class FiscalSigningProfile extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private FiscalSigningProfileStatus status = FiscalSigningProfileStatus.INACTIVE;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "key_provider", nullable = false, length = 60)
    private FiscalKeyProvider keyProvider = FiscalKeyProvider.MANUAL_PLACEHOLDER;

    @Column(name = "key_alias", length = 120)
    private String keyAlias;

    @Column(name = "public_key_fingerprint", length = 160)
    private String publicKeyFingerprint;

    @NotNull
    @Column(name = "algorithm", nullable = false, length = 40)
    private String algorithm = "RS256";

    @Column(name = "key_size")
    private Integer keySize;

    @Column(name = "rotated_at")
    private LocalDateTime rotatedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}

