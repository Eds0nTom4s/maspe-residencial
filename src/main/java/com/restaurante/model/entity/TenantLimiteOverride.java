package com.restaurante.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_limite_overrides", indexes = {
        @Index(name = "idx_tenant_limit_override_tenant", columnList = "tenant_id")
})
public class TenantLimiteOverride extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "max_instituicoes")
    private Integer maxInstituicoes;

    @Column(name = "max_unidades_atendimento")
    private Integer maxUnidadesAtendimento;

    @Column(name = "max_produtos")
    private Integer maxProdutos;

    @Column(name = "max_categorias")
    private Integer maxCategorias;

    @Column(name = "max_usuarios")
    private Integer maxUsuarios;

    @Column(name = "max_qr_codes")
    private Integer maxQrCodes;

    @Column(name = "max_dispositivos")
    private Integer maxDispositivos;

    @Column(name = "motivo", length = 500)
    private String motivo;

    @Column(name = "configurado_por", length = 120)
    private String configuradoPor;

    @Column(name = "configurado_em")
    private LocalDateTime configuradoEm;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    public TenantLimiteOverride() {
    }

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public Integer getMaxInstituicoes() {
        return maxInstituicoes;
    }

    public void setMaxInstituicoes(Integer maxInstituicoes) {
        this.maxInstituicoes = maxInstituicoes;
    }

    public Integer getMaxUnidadesAtendimento() {
        return maxUnidadesAtendimento;
    }

    public void setMaxUnidadesAtendimento(Integer maxUnidadesAtendimento) {
        this.maxUnidadesAtendimento = maxUnidadesAtendimento;
    }

    public Integer getMaxProdutos() {
        return maxProdutos;
    }

    public void setMaxProdutos(Integer maxProdutos) {
        this.maxProdutos = maxProdutos;
    }

    public Integer getMaxCategorias() {
        return maxCategorias;
    }

    public void setMaxCategorias(Integer maxCategorias) {
        this.maxCategorias = maxCategorias;
    }

    public Integer getMaxUsuarios() {
        return maxUsuarios;
    }

    public void setMaxUsuarios(Integer maxUsuarios) {
        this.maxUsuarios = maxUsuarios;
    }

    public Integer getMaxQrCodes() {
        return maxQrCodes;
    }

    public void setMaxQrCodes(Integer maxQrCodes) {
        this.maxQrCodes = maxQrCodes;
    }

    public Integer getMaxDispositivos() {
        return maxDispositivos;
    }

    public void setMaxDispositivos(Integer maxDispositivos) {
        this.maxDispositivos = maxDispositivos;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public String getConfiguradoPor() {
        return configuradoPor;
    }

    public void setConfiguradoPor(String configuradoPor) {
        this.configuradoPor = configuradoPor;
    }

    public LocalDateTime getConfiguradoEm() {
        return configuradoEm;
    }

    public void setConfiguradoEm(LocalDateTime configuradoEm) {
        this.configuradoEm = configuradoEm;
    }

    public Boolean getAtivo() {
        return ativo;
    }

    public void setAtivo(Boolean ativo) {
        this.ativo = ativo;
    }
}
