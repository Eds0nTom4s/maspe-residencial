package com.restaurante.model.entity;

import com.restaurante.model.enums.UnitOfMeasureStatus;
import com.restaurante.model.enums.UnitOfMeasureType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "unit_of_measure", indexes = {
        @Index(name = "idx_uom_code", columnList = "code")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class UnitOfMeasure extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @NotBlank
    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @NotBlank
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private UnitOfMeasureType type = UnitOfMeasureType.OTHER;

    @NotNull
    @Column(name = "decimal_allowed", nullable = false)
    private Boolean decimalAllowed = Boolean.FALSE;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private UnitOfMeasureStatus status = UnitOfMeasureStatus.ACTIVE;
}

