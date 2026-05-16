package com.restaurante.model.entity;

import com.restaurante.model.enums.QrCodeOperacionalTipo;
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
@Table(name = "qr_codes_operacionais", indexes = {
        @Index(name = "uk_qr_operacional_token", columnList = "token", unique = true),
        @Index(name = "idx_qr_operacional_tenant", columnList = "tenant_id"),
        @Index(name = "idx_qr_operacional_instituicao", columnList = "instituicao_id"),
        @Index(name = "idx_qr_operacional_unidade", columnList = "unidade_atendimento_id"),
        @Index(name = "idx_qr_operacional_tenant_ativo", columnList = "tenant_id, ativo"),
        @Index(name = "idx_qr_operacional_token", columnList = "token")
})
public class QrCodeOperacional extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instituicao_id", nullable = false)
    private Instituicao instituicao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_atendimento_id")
    private UnidadeAtendimento unidadeAtendimento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mesa_id")
    private Mesa mesa;

    @Column(name = "token", nullable = false, length = 64, unique = true)
    private String token;

    @Column(name = "nome", length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private QrCodeOperacionalTipo tipo;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "revogado", nullable = false)
    private Boolean revogado = false;

    @Column(name = "revogado_em")
    private LocalDateTime revogadoEm;

    public QrCodeOperacional() {
    }

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public Instituicao getInstituicao() {
        return instituicao;
    }

    public void setInstituicao(Instituicao instituicao) {
        this.instituicao = instituicao;
    }

    public UnidadeAtendimento getUnidadeAtendimento() {
        return unidadeAtendimento;
    }

    public void setUnidadeAtendimento(UnidadeAtendimento unidadeAtendimento) {
        this.unidadeAtendimento = unidadeAtendimento;
    }

    public Mesa getMesa() {
        return mesa;
    }

    public void setMesa(Mesa mesa) {
        this.mesa = mesa;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public QrCodeOperacionalTipo getTipo() {
        return tipo;
    }

    public void setTipo(QrCodeOperacionalTipo tipo) {
        this.tipo = tipo;
    }

    public Boolean getAtivo() {
        return ativo;
    }

    public void setAtivo(Boolean ativo) {
        this.ativo = ativo;
    }

    public Boolean getRevogado() {
        return revogado;
    }

    public void setRevogado(Boolean revogado) {
        this.revogado = revogado;
    }

    public LocalDateTime getRevogadoEm() {
        return revogadoEm;
    }

    public void setRevogadoEm(LocalDateTime revogadoEm) {
        this.revogadoEm = revogadoEm;
    }
}

