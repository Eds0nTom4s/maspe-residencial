package com.restaurante.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "rotas_producao_categoria", indexes = {
        @Index(name = "idx_rota_categoria_tenant", columnList = "tenant_id"),
        @Index(name = "idx_rota_categoria_tenant_categoria", columnList = "tenant_id, categoria_produto_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class RotaProducaoCategoria extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_produto_id", nullable = false)
    private CategoriaProduto categoriaProduto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_producao_id", nullable = false)
    private UnidadeProducao unidadeProducao;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "prioridade", nullable = false)
    private Integer prioridade = 0;

    @Column(name = "criado_em", nullable = false)
    private java.time.LocalDateTime criadoEm = java.time.LocalDateTime.now();

    @Column(name = "atualizado_em")
    private java.time.LocalDateTime atualizadoEm;
}

