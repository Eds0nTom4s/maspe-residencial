package com.restaurante.model.entity;

import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenants", indexes = {
        @Index(name = "idx_tenant_slug", columnList = "slug", unique = true),
        @Index(name = "idx_tenant_tenant_code", columnList = "tenant_code", unique = true),
        @Index(name = "idx_tenant_estado", columnList = "estado")
})
public class Tenant extends BaseEntity {

    @Column(name = "nome", nullable = false, length = 160)
    private String nome;

    @Column(name = "slug", nullable = false, unique = true, length = 80)
    private String slug;

    @Column(name = "tenant_code", nullable = false, unique = true, length = 20)
    private String tenantCode;

    @Column(name = "nif", length = 30)
    private String nif;

    @Column(name = "telefone", length = 20)
    private String telefone;

    @Column(name = "email", length = 120)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 40)
    private TenantTipo tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private TenantEstado estado;

    // Business template provenance (Prompt 39)
    @Column(name = "template_code", length = 60)
    private String templateCode;

    @Column(name = "template_version")
    private Integer templateVersion;

    @Column(name = "provisioned_at")
    private LocalDateTime provisionedAt;

    @Column(name = "provisioned_by", length = 120)
    private String provisionedBy;

    @Column(name = "provisioning_source", length = 80)
    private String provisioningSource;

    public Tenant() {
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getNif() {
        return nif;
    }

    public void setNif(String nif) {
        this.nif = nif;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public TenantTipo getTipo() {
        return tipo;
    }

    public void setTipo(TenantTipo tipo) {
        this.tipo = tipo;
    }

    public TenantEstado getEstado() {
        return estado;
    }

    public void setEstado(TenantEstado estado) {
        this.estado = estado;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public Integer getTemplateVersion() {
        return templateVersion;
    }

    public void setTemplateVersion(Integer templateVersion) {
        this.templateVersion = templateVersion;
    }

    public LocalDateTime getProvisionedAt() {
        return provisionedAt;
    }

    public void setProvisionedAt(LocalDateTime provisionedAt) {
        this.provisionedAt = provisionedAt;
    }

    public String getProvisionedBy() {
        return provisionedBy;
    }

    public void setProvisionedBy(String provisionedBy) {
        this.provisionedBy = provisionedBy;
    }

    public String getProvisioningSource() {
        return provisioningSource;
    }

    public void setProvisioningSource(String provisioningSource) {
        this.provisioningSource = provisioningSource;
    }
}
