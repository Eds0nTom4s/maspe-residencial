package com.restaurante.model.entity;

import com.restaurante.model.enums.UnidadeProducaoTipo;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "unidades_producao", indexes = {
        @Index(name = "idx_unidade_producao_tenant", columnList = "tenant_id"),
        @Index(name = "idx_unidade_producao_tenant_instituicao", columnList = "tenant_id, instituicao_id"),
        @Index(name = "idx_unidade_producao_tenant_ativo", columnList = "tenant_id, ativo")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class UnidadeProducao extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instituicao_id", nullable = false)
    private Instituicao instituicao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_atendimento_id")
    private UnidadeAtendimento unidadeAtendimento;

    @NotBlank
    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @NotBlank
    @Column(name = "codigo", nullable = false, length = 40)
    private String codigo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private UnidadeProducaoTipo tipo = UnidadeProducaoTipo.OUTRO;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "ordem", nullable = false)
    private Integer ordem = 0;

    @Column(name = "criado_em", nullable = false)
    private java.time.LocalDateTime criadoEm = java.time.LocalDateTime.now();

    @Column(name = "atualizado_em")
    private java.time.LocalDateTime atualizadoEm;
}

