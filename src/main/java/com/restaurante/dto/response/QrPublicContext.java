package com.restaurante.dto.response;

import com.restaurante.model.enums.QrCodeOperacionalTipo;

public class QrPublicContext {

    private Long qrId;
    private String token;

    private Long tenantId;
    private String tenantNome;
    private String tenantCode;

    private Long instituicaoId;
    private String instituicaoNome;

    private Long unidadeAtendimentoId;
    private String unidadeAtendimentoNome;

    private Long mesaId;
    private String mesaReferencia;
    private Integer mesaNumero;

    private QrCodeOperacionalTipo tipo;
    private String nome;
    private Boolean ativo;

    public Long getQrId() {
        return qrId;
    }

    public void setQrId(Long qrId) {
        this.qrId = qrId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantNome() {
        return tenantNome;
    }

    public void setTenantNome(String tenantNome) {
        this.tenantNome = tenantNome;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public Long getInstituicaoId() {
        return instituicaoId;
    }

    public void setInstituicaoId(Long instituicaoId) {
        this.instituicaoId = instituicaoId;
    }

    public String getInstituicaoNome() {
        return instituicaoNome;
    }

    public void setInstituicaoNome(String instituicaoNome) {
        this.instituicaoNome = instituicaoNome;
    }

    public Long getUnidadeAtendimentoId() {
        return unidadeAtendimentoId;
    }

    public void setUnidadeAtendimentoId(Long unidadeAtendimentoId) {
        this.unidadeAtendimentoId = unidadeAtendimentoId;
    }

    public String getUnidadeAtendimentoNome() {
        return unidadeAtendimentoNome;
    }

    public void setUnidadeAtendimentoNome(String unidadeAtendimentoNome) {
        this.unidadeAtendimentoNome = unidadeAtendimentoNome;
    }

    public Long getMesaId() {
        return mesaId;
    }

    public void setMesaId(Long mesaId) {
        this.mesaId = mesaId;
    }

    public String getMesaReferencia() {
        return mesaReferencia;
    }

    public void setMesaReferencia(String mesaReferencia) {
        this.mesaReferencia = mesaReferencia;
    }

    public Integer getMesaNumero() {
        return mesaNumero;
    }

    public void setMesaNumero(Integer mesaNumero) {
        this.mesaNumero = mesaNumero;
    }

    public QrCodeOperacionalTipo getTipo() {
        return tipo;
    }

    public void setTipo(QrCodeOperacionalTipo tipo) {
        this.tipo = tipo;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public Boolean getAtivo() {
        return ativo;
    }

    public void setAtivo(Boolean ativo) {
        this.ativo = ativo;
    }
}

