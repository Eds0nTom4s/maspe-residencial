package com.restaurante.model.entity;

import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenant_users", indexes = {
        @Index(name = "idx_tenant_user_tenant", columnList = "tenant_id"),
        @Index(name = "idx_tenant_user_user", columnList = "user_id")
})
public class TenantUser extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private TenantUserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private TenantUserEstado estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_atendimento_default_id")
    private UnidadeAtendimento unidadeAtendimentoDefault;

    public TenantUser() {
    }

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public TenantUserRole getRole() {
        return role;
    }

    public void setRole(TenantUserRole role) {
        this.role = role;
    }

    public TenantUserEstado getEstado() {
        return estado;
    }

    public void setEstado(TenantUserEstado estado) {
        this.estado = estado;
    }

    public UnidadeAtendimento getUnidadeAtendimentoDefault() {
        return unidadeAtendimentoDefault;
    }

    public void setUnidadeAtendimentoDefault(UnidadeAtendimento unidadeAtendimentoDefault) {
        this.unidadeAtendimentoDefault = unidadeAtendimentoDefault;
    }
}

