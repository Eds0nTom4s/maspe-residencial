package com.restaurante.model.entity;

import com.restaurante.model.enums.SubscricaoEstado;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "subscricoes", indexes = {
        @Index(name = "idx_subscricao_tenant", columnList = "tenant_id"),
        @Index(name = "idx_subscricao_plano", columnList = "plano_id"),
        @Index(name = "idx_subscricao_estado", columnList = "estado")
})
public class Subscricao extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plano_id", nullable = false)
    private Plano plano;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private SubscricaoEstado estado;

    @Column(name = "inicio_em", nullable = false)
    private LocalDate inicioEm;

    @Column(name = "fim_em")
    private LocalDate fimEm;

    @Column(name = "renovacao_automatica", nullable = false)
    private Boolean renovacaoAutomatica = false;

    public Subscricao() {
    }

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public Plano getPlano() {
        return plano;
    }

    public void setPlano(Plano plano) {
        this.plano = plano;
    }

    public SubscricaoEstado getEstado() {
        return estado;
    }

    public void setEstado(SubscricaoEstado estado) {
        this.estado = estado;
    }

    public LocalDate getInicioEm() {
        return inicioEm;
    }

    public void setInicioEm(LocalDate inicioEm) {
        this.inicioEm = inicioEm;
    }

    public LocalDate getFimEm() {
        return fimEm;
    }

    public void setFimEm(LocalDate fimEm) {
        this.fimEm = fimEm;
    }

    public Boolean getRenovacaoAutomatica() {
        return renovacaoAutomatica;
    }

    public void setRenovacaoAutomatica(Boolean renovacaoAutomatica) {
        this.renovacaoAutomatica = renovacaoAutomatica;
    }
}

