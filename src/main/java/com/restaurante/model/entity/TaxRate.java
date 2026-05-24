package com.restaurante.model.entity;

import com.restaurante.model.enums.TaxRateStatus;
import com.restaurante.model.enums.TaxType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tax_rates", indexes = {
        @Index(name = "uq_tax_rates_code", columnList = "country_code, code", unique = true),
        @Index(name = "idx_tax_rates_country_type_status", columnList = "country_code, tax_type, status")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TaxRate extends BaseEntity {

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_type", nullable = false, length = 30)
    private TaxType taxType;

    @Column(name = "code", nullable = false, length = 80)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "rate", nullable = false, precision = 9, scale = 4)
    private BigDecimal rate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TaxRateStatus status = TaxRateStatus.ACTIVE;

    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    @Column(name = "legal_reference", length = 255)
    private String legalReference;
}

