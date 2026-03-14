package com.restaurante.model.entity;

import com.restaurante.model.enums.StatusUnidadeConsumo;
import com.restaurante.model.enums.TipoUnidadeConsumo;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entity UnidadeDeConsumo
 */
@Entity
@Table(name = "unidades_consumo", indexes = {
    @Index(name = "idx_unidade_consumo_referencia", columnList = "referencia"),
    @Index(name = "idx_unidade_consumo_status", columnList = "status"),
    @Index(name = "idx_unidade_consumo_qr_code", columnList = "qr_code", unique = true),
    @Index(name = "idx_unidade_consumo_cliente", columnList = "cliente_id"),
    @Index(name = "idx_unidade_consumo_tipo", columnList = "tipo")
})
public class UnidadeDeConsumo extends BaseEntity {

    @NotBlank(message = "Referência da unidade é obrigatória")
    @Column(nullable = false, length = 100)
    private String referencia;

    @NotNull(message = "Tipo da unidade é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoUnidadeConsumo tipo = TipoUnidadeConsumo.MESA_FISICA;

    @Column(name = "numero")
    private Integer numero;

    @Column(name = "qr_code", unique = true, length = 100)
    private String qrCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusUnidadeConsumo status = StatusUnidadeConsumo.DISPONIVEL;

    @Column(name = "capacidade")
    private Integer capacidade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = true)
    private Cliente cliente;

    @Column(name = "modo_anonimo", nullable = false)
    private Boolean modoAnonimo = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendente_id")
    private Atendente atendente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_atendimento_id", nullable = false)
    private UnidadeAtendimento unidadeAtendimento;

    @Column(name = "aberta_em", nullable = false)
    private LocalDateTime abertaEm = LocalDateTime.now();

    @Column(name = "fechada_em")
    private LocalDateTime fechadaEm;

    @Deprecated
    @Transient
    private List<Pedido> pedidos = new ArrayList<>();

    public UnidadeDeConsumo() {}

    // Getters e Setters
    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }
    public TipoUnidadeConsumo getTipo() { return tipo; }
    public void setTipo(TipoUnidadeConsumo tipo) { this.tipo = tipo; }
    public Integer getNumero() { return numero; }
    public void setNumero(Integer numero) { this.numero = numero; }
    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }
    public StatusUnidadeConsumo getStatus() { return status; }
    public void setStatus(StatusUnidadeConsumo status) { this.status = status; }
    public Integer getCapacidade() { return capacidade; }
    public void setCapacidade(Integer capacidade) { this.capacidade = capacidade; }
    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }
    public Boolean getModoAnonimo() { return modoAnonimo; }
    public void setModoAnonimo(Boolean modoAnonimo) { this.modoAnonimo = modoAnonimo; }
    public Atendente getAtendente() { return atendente; }
    public void setAtendente(Atendente atendente) { this.atendente = atendente; }
    public UnidadeAtendimento getUnidadeAtendimento() { return unidadeAtendimento; }
    public void setUnidadeAtendimento(UnidadeAtendimento unidadeAtendimento) { this.unidadeAtendimento = unidadeAtendimento; }
    public LocalDateTime getAbertaEm() { return abertaEm; }
    public void setAbertaEm(LocalDateTime abertaEm) { this.abertaEm = abertaEm; }
    public LocalDateTime getFechadaEm() { return fechadaEm; }
    public void setFechadaEm(LocalDateTime fechadaEm) { this.fechadaEm = fechadaEm; }
    public List<Pedido> getPedidos() { return pedidos; }
    public void setPedidos(List<Pedido> pedidos) { this.pedidos = pedidos; }

    public BigDecimal calcularTotal() {
        return pedidos.stream()
            .map(Pedido::calcularTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean podeReceberPedidos() {
        return status == StatusUnidadeConsumo.OCUPADA;
    }

    public void fechar() {
        this.status = StatusUnidadeConsumo.FINALIZADA;
        this.fechadaEm = LocalDateTime.now();
    }

    public void atualizarStatus() {
        if (pedidos.isEmpty()) {
            this.status = StatusUnidadeConsumo.OCUPADA;
        } else {
            boolean todosPedidosEntregues = pedidos.stream()
                .allMatch(p -> p.getStatus() == com.restaurante.model.enums.StatusPedido.FINALIZADO);
            if (todosPedidosEntregues) {
                this.status = StatusUnidadeConsumo.AGUARDANDO_PAGAMENTO;
            } else {
                this.status = StatusUnidadeConsumo.OCUPADA;
            }
        }
    }

    public String getIdentificador() {
        return numero != null ? numero.toString() : referencia;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UnidadeDeConsumo)) return false;
        if (!super.equals(o)) return false;
        UnidadeDeConsumo u = (UnidadeDeConsumo) o;
        return Objects.equals(referencia, u.referencia) && Objects.equals(qrCode, u.qrCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), referencia, qrCode);
    }

    public static UnidadeDeConsumoBuilder builder() { return new UnidadeDeConsumoBuilder(); }

    public static class UnidadeDeConsumoBuilder {
        private String referencia;
        private TipoUnidadeConsumo tipo = TipoUnidadeConsumo.MESA_FISICA;
        private Integer numero;
        private String qrCode;
        private StatusUnidadeConsumo status = StatusUnidadeConsumo.DISPONIVEL;
        private Integer capacidade;
        private Cliente cliente;
        private Boolean modoAnonimo = false;
        private Atendente atendente;
        private UnidadeAtendimento unidadeAtendimento;
        private LocalDateTime abertaEm = LocalDateTime.now();
        private LocalDateTime fechadaEm;
        private List<Pedido> pedidos = new ArrayList<>();

        public UnidadeDeConsumoBuilder referencia(String referencia) { this.referencia = referencia; return this; }
        public UnidadeDeConsumoBuilder tipo(TipoUnidadeConsumo tipo) { this.tipo = tipo; return this; }
        public UnidadeDeConsumoBuilder numero(Integer numero) { this.numero = numero; return this; }
        public UnidadeDeConsumoBuilder qrCode(String qrCode) { this.qrCode = qrCode; return this; }
        public UnidadeDeConsumoBuilder status(StatusUnidadeConsumo status) { this.status = status; return this; }
        public UnidadeDeConsumoBuilder capacidade(Integer capacidade) { this.capacidade = capacidade; return this; }
        public UnidadeDeConsumoBuilder cliente(Cliente cliente) { this.cliente = cliente; return this; }
        public UnidadeDeConsumoBuilder modoAnonimo(Boolean modoAnonimo) { this.modoAnonimo = modoAnonimo; return this; }
        public UnidadeDeConsumoBuilder atendente(Atendente atendente) { this.atendente = atendente; return this; }
        public UnidadeDeConsumoBuilder unidadeAtendimento(UnidadeAtendimento u) { this.unidadeAtendimento = u; return this; }
        public UnidadeDeConsumoBuilder abertaEm(LocalDateTime abertaEm) { this.abertaEm = abertaEm; return this; }
        public UnidadeDeConsumoBuilder fechadaEm(LocalDateTime fechadaEm) { this.fechadaEm = fechadaEm; return this; }
        public UnidadeDeConsumoBuilder pedidos(List<Pedido> pedidos) { this.pedidos = pedidos; return this; }

        public UnidadeDeConsumo build() {
            UnidadeDeConsumo u = new UnidadeDeConsumo();
            u.referencia = referencia;
            u.tipo = tipo;
            u.numero = numero;
            u.qrCode = qrCode;
            u.status = status;
            u.capacidade = capacidade;
            u.cliente = cliente;
            u.modoAnonimo = modoAnonimo;
            u.atendente = atendente;
            u.unidadeAtendimento = unidadeAtendimento;
            u.abertaEm = abertaEm;
            u.fechadaEm = fechadaEm;
            u.pedidos = pedidos;
            return u;
        }
    }
}
