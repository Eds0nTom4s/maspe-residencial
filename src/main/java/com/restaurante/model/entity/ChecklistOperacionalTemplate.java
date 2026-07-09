package com.restaurante.model.entity;

import com.restaurante.model.enums.ChecklistEscopo;
import com.restaurante.model.enums.ChecklistTipo;
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
@Table(name = "checklist_operacional_templates", indexes = {
        @Index(name = "idx_checklist_template_tenant", columnList = "tenant_id"),
        @Index(name = "idx_checklist_template_tipo", columnList = "tipo"),
        @Index(name = "idx_checklist_template_ativo", columnList = "ativo")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ChecklistOperacionalTemplate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private ChecklistTipo tipo;

    @NotBlank
    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @NotNull
    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "escopo", nullable = false, length = 30)
    private ChecklistEscopo escopo = ChecklistEscopo.GLOBAL;
}

