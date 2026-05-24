package com.restaurante.model.entity;

import com.restaurante.model.enums.FiscalAuthority;
import com.restaurante.model.enums.OfficialFiscalEnvironment;
import com.restaurante.model.enums.OfficialFiscalSubmissionMode;
import com.restaurante.model.enums.TenantOfficialFiscalProfileStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tenant_official_fiscal_profiles", indexes = {
        @Index(name = "idx_tofp_tenant_status", columnList = "tenant_id, status")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TenantOfficialFiscalProfile extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private Tenant tenant;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private TenantOfficialFiscalProfileStatus status = TenantOfficialFiscalProfileStatus.NOT_CONFIGURED;

    @NotNull
    @Column(name = "country_code", nullable = false, length = 10)
    private String countryCode = "AO";

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "authority", nullable = false, length = 40)
    private FiscalAuthority authority = FiscalAuthority.AGT_AO;

    @NotNull
    @Column(name = "official_enabled", nullable = false)
    private boolean officialEnabled = false;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 40)
    private OfficialFiscalEnvironment environment = OfficialFiscalEnvironment.SANDBOX;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "submission_mode", nullable = false, length = 80)
    private OfficialFiscalSubmissionMode submissionMode = OfficialFiscalSubmissionMode.DISABLED;

    @Column(name = "taxpayer_number", length = 80)
    private String taxpayerNumber;

    @Column(name = "software_certificate_id", length = 120)
    private String softwareCertificateId;

    @Column(name = "software_name", length = 120)
    private String softwareName;

    @Column(name = "software_version", length = 60)
    private String softwareVersion;

    @Column(name = "producer_registration_id", length = 120)
    private String producerRegistrationId;

    @Column(name = "public_key_id", length = 120)
    private String publicKeyId;

    @Column(name = "taxpayer_key_id", length = 120)
    private String taxpayerKeyId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signing_profile_id")
    private FiscalSigningProfile signingProfile;

    @Column(name = "callback_url", length = 255)
    private String callbackUrl;
}

