package com.restaurante.model.entity;

import com.restaurante.model.enums.ProductTaxClassificationStatus;
import com.restaurante.model.enums.TaxCategory;
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
@Table(name = "product_tax_classifications", indexes = {
        @Index(name = "idx_product_tax_class_tenant_product", columnList = "tenant_id, product_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ProductTaxClassification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Produto product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_rate_id")
    private TaxRate taxRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_category", nullable = false, length = 40)
    private TaxCategory taxCategory = TaxCategory.STANDARD;

    @Column(name = "exempt_reason", length = 255)
    private String exemptReason;

    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ProductTaxClassificationStatus status = ProductTaxClassificationStatus.ACTIVE;
}

