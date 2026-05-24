package com.restaurante.model.entity;

import com.restaurante.model.enums.FiscalRegime;
import com.restaurante.model.enums.TenantTaxPolicyStatus;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_tax_policies", indexes = {
        @Index(name = "idx_tenant_tax_policy_tenant_status", columnList = "tenant_id, status")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TenantTaxPolicy extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "fiscal_regime", nullable = false, length = 40)
    private FiscalRegime fiscalRegime = FiscalRegime.NOT_CONFIGURED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_tax_rate_id")
    private TaxRate defaultTaxRate;

    @Column(name = "prices_include_tax", nullable = false)
    private boolean pricesIncludeTax = false;

    @Column(name = "allow_tax_exempt_items", nullable = false)
    private boolean allowTaxExemptItems = true;

    @Column(name = "require_tax_document_on_payment", nullable = false)
    private boolean requireTaxDocumentOnPayment = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TenantTaxPolicyStatus status = TenantTaxPolicyStatus.ACTIVE;

    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;
}

