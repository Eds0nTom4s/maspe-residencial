package com.restaurante.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "tenant_user_production_scopes", indexes = {
        @Index(name = "idx_tups_tenant", columnList = "tenant_id"),
        @Index(name = "idx_tups_tenant_user", columnList = "tenant_id, user_id"),
        @Index(name = "idx_tups_tenant_unidade_producao", columnList = "tenant_id, unidade_producao_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TenantUserProductionScope extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_producao_id", nullable = false)
    private UnidadeProducao unidadeProducao;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;
}

