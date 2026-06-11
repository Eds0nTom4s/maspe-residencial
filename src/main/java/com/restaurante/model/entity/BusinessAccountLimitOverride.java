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
@Table(name = "business_account_limit_overrides", indexes = {
        @Index(name = "idx_ba_limit_override_account", columnList = "business_account_id", unique = true)
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class BusinessAccountLimitOverride extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_account_id", nullable = false)
    private BusinessAccount businessAccount;

    @Column(name = "max_tenants")
    private Integer maxTenants;

    @Column(name = "max_instituicoes")
    private Integer maxInstituicoes;

    @Column(name = "max_unidades_atendimento")
    private Integer maxUnidadesAtendimento;

    @Column(name = "max_dispositivos")
    private Integer maxDispositivos;

    @Column(name = "max_produtos")
    private Integer maxProdutos;

    @Column(name = "max_categorias")
    private Integer maxCategorias;

    @Column(name = "max_usuarios")
    private Integer maxUsuarios;

    @Column(name = "max_qr_codes")
    private Integer maxQrCodes;

    @Column(name = "max_pedidos_mes")
    private Integer maxPedidosMes;

    @Column(name = "observacao", length = 500)
    private String observacao;

    @Column(name = "configurado_por", length = 120)
    private String configuradoPor;

    @Column(name = "configurado_em")
    private LocalDateTime configuradoEm;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;
}
