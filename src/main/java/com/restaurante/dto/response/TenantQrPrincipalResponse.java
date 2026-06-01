package com.restaurante.dto.response;

import java.time.OffsetDateTime;

public class TenantQrPrincipalResponse {

    private Long tenantId;
    private String tenantNome;

    private Long qrCodeId;
    private String tipo;
    private String status;

    private String qrUrlPublica;
    private String cardapioUrlPublica;

    private Long unidadeAtendimentoId;
    private String unidadeNome;

    private OffsetDateTime geradoEm;
    private OffsetDateTime atualizadoEm;

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

    public Long getQrCodeId() {
        return qrCodeId;
    }

    public void setQrCodeId(Long qrCodeId) {
        this.qrCodeId = qrCodeId;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getQrUrlPublica() {
        return qrUrlPublica;
    }

    public void setQrUrlPublica(String qrUrlPublica) {
        this.qrUrlPublica = qrUrlPublica;
    }

    public String getCardapioUrlPublica() {
        return cardapioUrlPublica;
    }

    public void setCardapioUrlPublica(String cardapioUrlPublica) {
        this.cardapioUrlPublica = cardapioUrlPublica;
    }

    public Long getUnidadeAtendimentoId() {
        return unidadeAtendimentoId;
    }

    public void setUnidadeAtendimentoId(Long unidadeAtendimentoId) {
        this.unidadeAtendimentoId = unidadeAtendimentoId;
    }

    public String getUnidadeNome() {
        return unidadeNome;
    }

    public void setUnidadeNome(String unidadeNome) {
        this.unidadeNome = unidadeNome;
    }

    public OffsetDateTime getGeradoEm() {
        return geradoEm;
    }

    public void setGeradoEm(OffsetDateTime geradoEm) {
        this.geradoEm = geradoEm;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(OffsetDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }
}
