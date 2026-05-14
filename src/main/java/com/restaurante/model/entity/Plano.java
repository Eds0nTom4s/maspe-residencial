package com.restaurante.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "planos", indexes = {
        @Index(name = "idx_plano_codigo", columnList = "codigo", unique = true),
        @Index(name = "idx_plano_ativo", columnList = "ativo")
})
public class Plano extends BaseEntity {

    @Column(name = "codigo", nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "descricao", columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "preco_mensal", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoMensal = BigDecimal.ZERO;

    @Column(name = "max_instituicoes", nullable = false)
    private Integer maxInstituicoes;

    @Column(name = "max_unidades_atendimento", nullable = false)
    private Integer maxUnidadesAtendimento;

    @Column(name = "max_produtos", nullable = false)
    private Integer maxProdutos;

    @Column(name = "max_usuarios", nullable = false)
    private Integer maxUsuarios;

    @Column(name = "max_qr_codes", nullable = false)
    private Integer maxQrCodes;

    @Column(name = "max_dispositivos", nullable = false)
    private Integer maxDispositivos;

    @Column(name = "permite_multi_instituicao", nullable = false)
    private Boolean permiteMultiInstituicao = false;

    @Column(name = "permite_pedidos_qr", nullable = false)
    private Boolean permitePedidosQr = true;

    @Column(name = "permite_pos", nullable = false)
    private Boolean permitePos = true;

    @Column(name = "permite_offline", nullable = false)
    private Boolean permiteOffline = false;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    public Plano() {
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public BigDecimal getPrecoMensal() {
        return precoMensal;
    }

    public void setPrecoMensal(BigDecimal precoMensal) {
        this.precoMensal = precoMensal;
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

    public Boolean getPermiteMultiInstituicao() {
        return permiteMultiInstituicao;
    }

    public void setPermiteMultiInstituicao(Boolean permiteMultiInstituicao) {
        this.permiteMultiInstituicao = permiteMultiInstituicao;
    }

    public Boolean getPermitePedidosQr() {
        return permitePedidosQr;
    }

    public void setPermitePedidosQr(Boolean permitePedidosQr) {
        this.permitePedidosQr = permitePedidosQr;
    }

    public Boolean getPermitePos() {
        return permitePos;
    }

    public void setPermitePos(Boolean permitePos) {
        this.permitePos = permitePos;
    }

    public Boolean getPermiteOffline() {
        return permiteOffline;
    }

    public void setPermiteOffline(Boolean permiteOffline) {
        this.permiteOffline = permiteOffline;
    }

    public Boolean getAtivo() {
        return ativo;
    }

    public void setAtivo(Boolean ativo) {
        this.ativo = ativo;
    }
}

