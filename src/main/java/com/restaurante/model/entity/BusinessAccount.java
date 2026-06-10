package com.restaurante.model.entity;

import com.restaurante.model.enums.BusinessAccountEstado;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "business_accounts", indexes = {
        @Index(name = "idx_business_account_slug", columnList = "slug", unique = true),
        @Index(name = "idx_business_account_nif", columnList = "nif"),
        @Index(name = "idx_business_account_estado", columnList = "estado"),
        @Index(name = "idx_business_account_responsavel", columnList = "responsavel_user_id")
})
public class BusinessAccount extends BaseEntity {

    @Column(name = "nome", nullable = false, length = 160)
    private String nome;

    @Column(name = "slug", nullable = false, unique = true, length = 80)
    private String slug;

    @Column(name = "nif", length = 30)
    private String nif;

    @Column(name = "email", length = 120)
    private String email;

    @Column(name = "telefone", length = 20)
    private String telefone;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private BusinessAccountEstado estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_user_id")
    private User responsavel;

    @Column(name = "observacao", length = 500)
    private String observacao;

    @Column(name = "provisioned_at")
    private LocalDateTime provisionedAt;

    @Column(name = "provisioned_by", length = 120)
    private String provisionedBy;

    public BusinessAccount() {
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

    public String getNif() {
        return nif;
    }

    public void setNif(String nif) {
        this.nif = nif;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public BusinessAccountEstado getEstado() {
        return estado;
    }

    public void setEstado(BusinessAccountEstado estado) {
        this.estado = estado;
    }

    public User getResponsavel() {
        return responsavel;
    }

    public void setResponsavel(User responsavel) {
        this.responsavel = responsavel;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
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
}
