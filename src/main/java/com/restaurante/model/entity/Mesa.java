package com.restaurante.model.entity;

import com.restaurante.model.enums.TipoUnidadeConsumo;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;

import java.util.ArrayList;
import java.util.List;

/**
 * Entidade Mesa — recurso físico PERMANENTE.
 *
 * <p>Representa uma mesa (ou ponto de consumo) do mundo real.
 * É criada uma única vez pelo ADMIN e NUNCA é "finalizada" ou "fechada".
 *
 * <p>Invariantes:
 * <ul>
 *   <li>Nunca possui status de ocupação persistido.</li>
 *   <li>O QR Code é fixo e vinculado à mesa, não à sessão.</li>
 *   <li>Status de ocupação é DERIVADO via SessaoConsumo:
 *       <pre>OCUPADA ≡ EXISTS SessaoConsumo WHERE mesa = this AND status = ABERTA</pre>
 *   </li>
 *   <li>Desativar ({@code ativa = false}) é a única operação administrativa permitida.</li>
 * </ul>
 */
@Entity
@Table(name = "mesas", indexes = {
    @Index(name = "idx_mesa_referencia", columnList = "referencia"),
    @Index(name = "idx_mesa_qr_code", columnList = "qr_code", unique = true),
    @Index(name = "idx_mesa_ativa", columnList = "ativa"),
    @Index(name = "idx_mesa_unidade_atendimento", columnList = "unidade_atendimento_id")
})
public class Mesa extends BaseEntity {

    /**
     * Referência humana da mesa.
     * Exemplos: "Mesa 10", "Quarto 205", "Área VIP"
     */
    @NotBlank(message = "Referência da mesa é obrigatória")
    @Column(nullable = false, length = 100)
    private String referencia;

    /**
     * Número da mesa (quando aplicável).
     */
    @Column(name = "numero")
    private Integer numero;

    /**
     * QR Code fixo da mesa — nunca muda entre sessões.
     * Escanear este código inicia uma nova SessaoConsumo.
     */
    @Column(name = "qr_code", unique = true, length = 100)
    private String qrCode;

    /**
     * Capacidade máxima de pessoas.
     */
    @Column(name = "capacidade")
    private Integer capacidade;

    /**
     * Flag de ativação administrativa.
     * Mesas inativas não podem receber novas sessões.
     */
    @Column(name = "ativa", nullable = false)
    private Boolean ativa = true;

    /**
     * Tipo de ponto de consumo.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoUnidadeConsumo tipo = TipoUnidadeConsumo.MESA_FISICA;

    /**
     * Unidade de atendimento (restaurante / bar / evento) à qual a mesa pertence.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_atendimento_id", nullable = false)
    private UnidadeAtendimento unidadeAtendimento;

    /**
     * Histórico de sessões de consumo desta mesa.
     * Apenas leitura — gerenciado pelo lado proprietário (SessaoConsumo).
     */
    @OneToMany(mappedBy = "mesa", fetch = FetchType.LAZY)
    private List<SessaoConsumo> sessoes = new ArrayList<>();

    /**
     * Retorna identificador legível: número ou referência.
     */
    public String getIdentificador() {
        return numero != null ? numero.toString() : referencia;
    }

    public Mesa() {}

    public Mesa(String referencia, Integer numero, String qrCode, Integer capacidade, Boolean ativa, TipoUnidadeConsumo tipo, UnidadeAtendimento unidadeAtendimento, List<SessaoConsumo> sessoes) {
        this.referencia = referencia;
        this.numero = numero;
        this.qrCode = qrCode;
        this.capacidade = capacidade;
        this.ativa = ativa != null ? ativa : true;
        this.tipo = tipo != null ? tipo : TipoUnidadeConsumo.MESA_FISICA;
        this.unidadeAtendimento = unidadeAtendimento;
        this.sessoes = sessoes != null ? sessoes : new ArrayList<>();
    }

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }

    public Integer getNumero() { return numero; }
    public void setNumero(Integer numero) { this.numero = numero; }

    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }

    public Integer getCapacidade() { return capacidade; }
    public void setCapacidade(Integer capacidade) { this.capacidade = capacidade; }

    public Boolean getAtiva() { return ativa; }
    public void setAtiva(Boolean ativa) { this.ativa = ativa; }

    public TipoUnidadeConsumo getTipo() { return tipo; }
    public void setTipo(TipoUnidadeConsumo tipo) { this.tipo = tipo; }

    public UnidadeAtendimento getUnidadeAtendimento() { return unidadeAtendimento; }
    public void setUnidadeAtendimento(UnidadeAtendimento unidadeAtendimento) { this.unidadeAtendimento = unidadeAtendimento; }

    public List<SessaoConsumo> getSessoes() { return sessoes; }
    public void setSessoes(List<SessaoConsumo> sessoes) { this.sessoes = sessoes; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Mesa mesa = (Mesa) o;
        return Objects.equals(referencia, mesa.referencia) &&
               Objects.equals(numero, mesa.numero) &&
               Objects.equals(qrCode, mesa.qrCode) &&
               Objects.equals(capacidade, mesa.capacidade) &&
               Objects.equals(ativa, mesa.ativa) &&
               tipo == mesa.tipo &&
               Objects.equals(unidadeAtendimento, mesa.unidadeAtendimento) &&
               Objects.equals(sessoes, mesa.sessoes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), referencia, numero, qrCode, capacidade, ativa, tipo, unidadeAtendimento, sessoes);
    }

    public static MesaBuilder builder() {
        return new MesaBuilder();
    }

    public static class MesaBuilder {
        private String referencia;
        private Integer numero;
        private String qrCode;
        private Integer capacidade;
        private Boolean ativa;
        private TipoUnidadeConsumo tipo;
        private UnidadeAtendimento unidadeAtendimento;
        private List<SessaoConsumo> sessoes;

        MesaBuilder() {}

        public MesaBuilder referencia(String referencia) {
            this.referencia = referencia;
            return this;
        }

        public MesaBuilder numero(Integer numero) {
            this.numero = numero;
            return this;
        }

        public MesaBuilder qrCode(String qrCode) {
            this.qrCode = qrCode;
            return this;
        }

        public MesaBuilder capacidade(Integer capacidade) {
            this.capacidade = capacidade;
            return this;
        }

        public MesaBuilder ativa(Boolean ativa) {
            this.ativa = ativa;
            return this;
        }

        public MesaBuilder tipo(TipoUnidadeConsumo tipo) {
            this.tipo = tipo;
            return this;
        }

        public MesaBuilder unidadeAtendimento(UnidadeAtendimento unidadeAtendimento) {
            this.unidadeAtendimento = unidadeAtendimento;
            return this;
        }

        public MesaBuilder sessoes(List<SessaoConsumo> sessoes) {
            this.sessoes = sessoes;
            return this;
        }

        public Mesa build() {
            return new Mesa(this.referencia, this.numero, this.qrCode, this.capacidade, this.ativa, this.tipo, this.unidadeAtendimento, this.sessoes);
        }
    }
}
