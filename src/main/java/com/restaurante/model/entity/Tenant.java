package com.restaurante.model.entity;

import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

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
}

