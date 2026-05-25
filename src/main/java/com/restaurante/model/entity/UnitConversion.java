package com.restaurante.model.entity;

import com.restaurante.model.enums.UnitConversionStatus;
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

import java.math.BigDecimal;

@Entity
@Table(name = "unit_conversions", indexes = {
        @Index(name = "idx_unit_conv_from", columnList = "from_unit_id"),
        @Index(name = "idx_unit_conv_to", columnList = "to_unit_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class UnitConversion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_unit_id", nullable = false)
    private UnitOfMeasure fromUnit;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_unit_id", nullable = false)
    private UnitOfMeasure toUnit;

    @NotNull
    @Column(name = "factor", nullable = false, precision = 19, scale = 8)
    private BigDecimal factor;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private UnitConversionStatus status = UnitConversionStatus.ACTIVE;
}

