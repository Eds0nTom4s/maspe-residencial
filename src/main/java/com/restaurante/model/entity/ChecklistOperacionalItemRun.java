package com.restaurante.model.entity;

import com.restaurante.model.enums.ChecklistItemRunStatus;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "checklist_operacional_item_runs", indexes = {
        @Index(name = "idx_checklist_item_run_run", columnList = "run_id"),
        @Index(name = "idx_checklist_item_run_codigo", columnList = "codigo"),
        @Index(name = "idx_checklist_item_run_status", columnList = "status")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ChecklistOperacionalItemRun extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private ChecklistOperacionalRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_template_id")
    private ChecklistOperacionalItemTemplate itemTemplate;

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
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_resposta", nullable = false, length = 20)
    private ChecklistTipoResposta tipoResposta = ChecklistTipoResposta.BOOLEAN;

    @Column(name = "valor_boolean")
    private Boolean valorBoolean;

    @Column(name = "valor_texto", length = 1000)
    private String valorTexto;

    @Column(name = "valor_numero", precision = 18, scale = 4)
    private BigDecimal valorNumero;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChecklistItemRunStatus status = ChecklistItemRunStatus.PENDENTE;

    @Column(name = "observacao", length = 500)
    private String observacao;

    @Column(name = "respondido_em")
    private LocalDateTime respondidoEm;
}

