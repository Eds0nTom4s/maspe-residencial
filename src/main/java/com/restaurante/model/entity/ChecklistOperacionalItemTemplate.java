package com.restaurante.model.entity;

import com.restaurante.model.enums.ChecklistTipoResposta;
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
@Table(name = "checklist_operacional_item_templates", indexes = {
        @Index(name = "idx_checklist_item_template_template", columnList = "template_id"),
        @Index(name = "idx_checklist_item_template_codigo", columnList = "codigo"),
        @Index(name = "idx_checklist_item_template_ativo", columnList = "ativo")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ChecklistOperacionalItemTemplate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private ChecklistOperacionalTemplate template;

    @NotBlank
    @Column(name = "codigo", nullable = false, length = 60)
    private String codigo;

    @NotBlank
    @Column(name = "descricao", nullable = false, length = 255)
    private String descricao;

    @NotNull
    @Column(name = "obrigatorio", nullable = false)
    private Boolean obrigatorio = true;

    @NotNull
    @Column(name = "ordem", nullable = false)
    private Integer ordem = 0;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_resposta", nullable = false, length = 20)
    private ChecklistTipoResposta tipoResposta = ChecklistTipoResposta.BOOLEAN;

    @NotNull
    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;
}

