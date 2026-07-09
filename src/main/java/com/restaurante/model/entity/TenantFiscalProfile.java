package com.restaurante.model.entity;

import com.restaurante.model.enums.FiscalRegime;
import com.restaurante.model.enums.TenantFiscalProfileStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tenant_fiscal_profiles", indexes = {
        @Index(name = "uq_tenant_fiscal_profile_tenant", columnList = "tenant_id", unique = true)
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TenantFiscalProfile extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TenantFiscalProfileStatus status = TenantFiscalProfileStatus.INACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "fiscal_regime", nullable = false, length = 40)
    private FiscalRegime fiscalRegime = FiscalRegime.NOT_CONFIGURED;

    @Column(name = "taxpayer_number", length = 40)
    private String taxpayerNumber;

    @Column(name = "legal_name", length = 255)
    private String legalName;

    @Column(name = "commercial_name", length = 255)
    private String commercialName;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode = "AO";

    @Column(name = "province", length = 120)
    private String province;

    @Column(name = "municipality", length = 120)
    private String municipality;

    @Column(name = "address", length = 255)
    private String address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_tax_policy_id")
    private TenantTaxPolicy defaultTaxPolicy;

    @Column(name = "invoice_required", nullable = false)
    private boolean invoiceRequired = false;

    @Column(name = "fiscal_document_enabled", nullable = false)
    private boolean fiscalDocumentEnabled = false;
}

