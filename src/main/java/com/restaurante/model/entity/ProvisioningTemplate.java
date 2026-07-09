package com.restaurante.model.entity;

import com.restaurante.model.enums.TenantTipo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "provisioning_templates", indexes = {
        @Index(name = "idx_provisioning_template_codigo", columnList = "codigo", unique = true),
        @Index(name = "idx_provisioning_template_ativo", columnList = "ativo"),
        @Index(name = "idx_provisioning_template_tipo_tenant", columnList = "tipo_tenant")
})
public class ProvisioningTemplate extends BaseEntity {

    @Column(name = "codigo", nullable = false, unique = true, length = 60)
    private String codigo;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_tenant", nullable = false, length = 40)
    private TenantTipo tipoTenant;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "configuracao_json", columnDefinition = "TEXT")
    private String configuracaoJson;

    public ProvisioningTemplate() {
    }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public TenantTipo getTipoTenant() { return tipoTenant; }
    public void setTipoTenant(TenantTipo tipoTenant) { this.tipoTenant = tipoTenant; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }

    public String getConfiguracaoJson() { return configuracaoJson; }
    public void setConfiguracaoJson(String configuracaoJson) { this.configuracaoJson = configuracaoJson; }
}

