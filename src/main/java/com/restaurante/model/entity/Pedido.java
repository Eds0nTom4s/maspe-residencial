package com.restaurante.model.entity;

import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.TipoPagamentoPedido;
import jakarta.persistence.*;
import java.util.Objects;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidade Pedido
 * Representa um pedido feito por um cliente em uma mesa
 * Cada pedido contém múltiplos itens (produtos)
 */
@Entity
@Table(name = "pedidos", indexes = {
    @Index(name = "idx_pedido_sessao_consumo", columnList = "sessao_consumo_id"),
    @Index(name = "idx_pedido_status", columnList = "status"),
    @Index(name = "idx_pedido_status_financeiro", columnList = "status_financeiro"),
    @Index(name = "idx_pedido_numero", columnList = "numero")
})
public class Pedido extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String numero; // Ex: PED-20260208-001

    // Relacionamento OBRIGATÓRIO com SessaoConsumo
    // Pedidos são vinculados à sessão, não diretamente à mesa.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_consumo_id", nullable = false)
    private SessaoConsumo sessaoConsumo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPedido status = StatusPedido.CRIADO;

    /**
     * Status financeiro (separado do status operacional)
     * - NAO_PAGO: Aguardando pagamento (pós-pago)
     * - PAGO: Pagamento confirmado (pré-pago ou confirmado)
     * - ESTORNADO: Pedido cancelado com devolução
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_financeiro", nullable = false, length = 20)
    private StatusFinanceiroPedido statusFinanceiro = StatusFinanceiroPedido.NAO_PAGO;

    /**
     * Tipo de pagamento do pedido
     * - PRE_PAGO: Débito automático do Fundo de Consumo
     * - POS_PAGO: Pagamento posterior (apenas GERENTE/ADMIN)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pagamento", nullable = false, length = 20)
    private TipoPagamentoPedido tipoPagamento = TipoPagamentoPedido.PRE_PAGO;

    /**
     * Data/hora do pagamento
     */
    @Column(name = "pago_em")
    private LocalDateTime pagoEm;

    @Column(length = 500)
    private String observacoes;

    // Relacionamento com itens do pedido
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ItemPedido> itens = new ArrayList<>();

    // Relacionamento com SubPedidos
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SubPedido> subPedidos = new ArrayList<>();

    @Column(name = "total", precision = 10, scale = 2)
    private BigDecimal total;

    public Pedido() {}

    public Pedido(String numero, SessaoConsumo sessaoConsumo, StatusPedido status, StatusFinanceiroPedido statusFinanceiro, TipoPagamentoPedido tipoPagamento, LocalDateTime pagoEm, String observacoes, List<ItemPedido> itens, List<SubPedido> subPedidos, BigDecimal total) {
        this.numero = numero;
        this.sessaoConsumo = sessaoConsumo;
        this.status = status != null ? status : StatusPedido.CRIADO;
        this.statusFinanceiro = statusFinanceiro != null ? statusFinanceiro : StatusFinanceiroPedido.NAO_PAGO;
        this.tipoPagamento = tipoPagamento != null ? tipoPagamento : TipoPagamentoPedido.PRE_PAGO;
        this.pagoEm = pagoEm;
        this.observacoes = observacoes;
        this.itens = itens != null ? itens : new ArrayList<>();
        this.subPedidos = subPedidos != null ? subPedidos : new ArrayList<>();
        this.total = total;
    }

    public void setNumero(String numero) { this.numero = numero; }
    public void setSessaoConsumo(SessaoConsumo sessaoConsumo) { this.sessaoConsumo = sessaoConsumo; }

    public StatusFinanceiroPedido getStatusFinanceiro() { return statusFinanceiro; }
    public void setStatusFinanceiro(StatusFinanceiroPedido statusFinanceiro) { this.statusFinanceiro = statusFinanceiro; }

    public TipoPagamentoPedido getTipoPagamento() { return tipoPagamento; }
    public void setTipoPagamento(TipoPagamentoPedido tipoPagamento) { this.tipoPagamento = tipoPagamento; }

    public LocalDateTime getPagoEm() { return pagoEm; }
    public void setPagoEm(LocalDateTime pagoEm) { this.pagoEm = pagoEm; }

    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }

    public List<ItemPedido> getItens() { return itens; }
    public void setItens(List<ItemPedido> itens) { this.itens = itens; }

    public List<SubPedido> getSubPedidos() { return subPedidos; }
    public void setSubPedidos(List<SubPedido> subPedidos) { this.subPedidos = subPedidos; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Pedido pedido = (Pedido) o;
        return Objects.equals(numero, pedido.numero) &&
               Objects.equals(sessaoConsumo, pedido.sessaoConsumo) &&
               status == pedido.status &&
               statusFinanceiro == pedido.statusFinanceiro &&
               tipoPagamento == pedido.tipoPagamento &&
               Objects.equals(pagoEm, pedido.pagoEm) &&
               Objects.equals(observacoes, pedido.observacoes) &&
               Objects.equals(itens, pedido.itens) &&
               Objects.equals(subPedidos, pedido.subPedidos) &&
               Objects.equals(total, pedido.total);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), numero, sessaoConsumo, status, statusFinanceiro, tipoPagamento, pagoEm, observacoes, itens, subPedidos, total);
    }

    public static PedidoBuilder builder() {
        return new PedidoBuilder();
    }

    public static class PedidoBuilder {
        private String numero;
        private SessaoConsumo sessaoConsumo;
        private StatusPedido status;
        private StatusFinanceiroPedido statusFinanceiro;
        private TipoPagamentoPedido tipoPagamento;
        private LocalDateTime pagoEm;
        private String observacoes;
        private List<ItemPedido> itens;
        private List<SubPedido> subPedidos;
        private BigDecimal total;

        PedidoBuilder() {}

        public PedidoBuilder numero(String numero) {
            this.numero = numero;
            return this;
        }

        public PedidoBuilder sessaoConsumo(SessaoConsumo sessaoConsumo) {
            this.sessaoConsumo = sessaoConsumo;
            return this;
        }

        public PedidoBuilder status(StatusPedido status) {
            this.status = status;
            return this;
        }

        public PedidoBuilder statusFinanceiro(StatusFinanceiroPedido statusFinanceiro) {
            this.statusFinanceiro = statusFinanceiro;
            return this;
        }

        public PedidoBuilder tipoPagamento(TipoPagamentoPedido tipoPagamento) {
            this.tipoPagamento = tipoPagamento;
            return this;
        }

        public PedidoBuilder pagoEm(LocalDateTime pagoEm) {
            this.pagoEm = pagoEm;
            return this;
        }

        public PedidoBuilder observacoes(String observacoes) {
            this.observacoes = observacoes;
            return this;
        }

        public PedidoBuilder itens(List<ItemPedido> itens) {
            this.itens = itens;
            return this;
        }

        public PedidoBuilder subPedidos(List<SubPedido> subPedidos) {
            this.subPedidos = subPedidos;
            return this;
        }

        public PedidoBuilder total(BigDecimal total) {
            this.total = total;
            return this;
        }

        public Pedido build() {
            return new Pedido(this.numero, this.sessaoConsumo, this.status, this.statusFinanceiro, this.tipoPagamento, this.pagoEm, this.observacoes, this.itens, this.subPedidos, this.total);
        }
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public void setStatus(StatusPedido status) {
        this.status = status;
    }

    /**
     * Calcula o total do pedido somando todos os subpedidos
     */
    public BigDecimal calcularTotal() {
        BigDecimal totalCalculado = subPedidos.stream()
            .map(sp -> sp.getTotal() != null ? sp.getTotal() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        this.total = totalCalculado;
        return totalCalculado;
    }

    /**
     * Adiciona um item ao pedido
     */
    public void adicionarItem(ItemPedido item) {
        itens.add(item);
        item.setPedido(this);
        calcularTotal();
    }

    /**
     * Remove um item do pedido
     */
    public void removerItem(ItemPedido item) {
        itens.remove(item);
        item.setPedido(null);
        calcularTotal();
    }

    /**
     * Verifica se o pedido pode ser cancelado
     */
    public boolean podeCancelar() {
        return !status.isTerminal();
    }

    /**
     * Verifica se pedido está pago
     */
    public boolean isPago() {
        return statusFinanceiro.isPago();
    }

    /**
     * Verifica se pode estornar
     */
    public boolean podeEstornar() {
        return statusFinanceiro.podeEstornar();
    }

    /**
     * Marca pedido como pago
     */
    public void marcarComoPago() {
        this.statusFinanceiro = StatusFinanceiroPedido.PAGO;
        this.pagoEm = LocalDateTime.now();
    }

    /**
     * Estorna pagamento (cancelamento)
     */
    public void estornar() {
        if (!podeEstornar()) {
            throw new IllegalStateException("Pedido não pode ser estornado no estado atual: " + statusFinanceiro);
        }
        this.statusFinanceiro = StatusFinanceiroPedido.ESTORNADO;
    }

    public String getNumero() {
        return this.numero;
    }

    public SessaoConsumo getSessaoConsumo() {
        return this.sessaoConsumo;
    }
}
