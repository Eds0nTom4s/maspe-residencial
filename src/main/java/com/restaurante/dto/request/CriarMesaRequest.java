package com.restaurante.dto.request;

import com.restaurante.model.enums.TipoUnidadeConsumo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request para criação de uma nova Mesa física.
 * Operação exclusiva do ADMIN.
 * A mesa representa um recurso permanente — criada uma única vez.
 */
public class CriarMesaRequest {

    @NotBlank(message = "Referência é obrigatória")
    private String referencia;

    private TipoUnidadeConsumo tipo;

    private Integer numero;

    private String qrCode;

    private Integer capacidade;

    @NotNull(message = "Unidade de atendimento é obrigatória")
    private Long unidadeAtendimentoId;

    public CriarMesaRequest() {}

    public CriarMesaRequest(String referencia, TipoUnidadeConsumo tipo, Integer numero,
                             String qrCode, Integer capacidade, Long unidadeAtendimentoId) {
        this.referencia = referencia;
        this.tipo = tipo;
        this.numero = numero;
        this.qrCode = qrCode;
        this.capacidade = capacidade;
        this.unidadeAtendimentoId = unidadeAtendimentoId;
    }

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }

    public TipoUnidadeConsumo getTipo() { return tipo; }
    public void setTipo(TipoUnidadeConsumo tipo) { this.tipo = tipo; }

    public Integer getNumero() { return numero; }
    public void setNumero(Integer numero) { this.numero = numero; }

    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }

    public Integer getCapacidade() { return capacidade; }
    public void setCapacidade(Integer capacidade) { this.capacidade = capacidade; }

    public Long getUnidadeAtendimentoId() { return unidadeAtendimentoId; }
    public void setUnidadeAtendimentoId(Long unidadeAtendimentoId) { this.unidadeAtendimentoId = unidadeAtendimentoId; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String referencia;
        private TipoUnidadeConsumo tipo;
        private Integer numero;
        private String qrCode;
        private Integer capacidade;
        private Long unidadeAtendimentoId;

        public Builder referencia(String v) { this.referencia = v; return this; }
        public Builder tipo(TipoUnidadeConsumo v) { this.tipo = v; return this; }
        public Builder numero(Integer v) { this.numero = v; return this; }
        public Builder qrCode(String v) { this.qrCode = v; return this; }
        public Builder capacidade(Integer v) { this.capacidade = v; return this; }
        public Builder unidadeAtendimentoId(Long v) { this.unidadeAtendimentoId = v; return this; }

        public CriarMesaRequest build() {
            return new CriarMesaRequest(referencia, tipo, numero, qrCode, capacidade, unidadeAtendimentoId);
        }
    }
}
