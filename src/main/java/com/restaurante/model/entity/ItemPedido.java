package com.restaurante.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

/**
 * Entidade ItemPedido
 * Representa um produto específico dentro de um pedido
 * Inclui quantidade, preço unitário e observações
 */
@Entity
@Table(name = "itens_pedido", indexes = {
    @Index(name = "idx_item_pedido", columnList = "pedido_id"),
    @Index(name = "idx_item_produto", columnList = "produto_id")
})
public class ItemPedido extends BaseEntity {

    // Relacionamento com Pedido (mantido para compatibilidade, nullable = true)
    // Opcional arquiteturalmente: O fluxo primário agora é ItemPedido -> SubPedido.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = true)
    private Pedido pedido;

    // Relacionamento OBRIGATÓRIO com SubPedido (novo modelo)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_pedido_id", nullable = false)
    private SubPedido subPedido;

    // Relacionamento OBRIGATÓRIO com Produto
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @NotNull(message = "Quantidade é obrigatória")
    @Min(value = 1, message = "Quantidade deve ser maior que zero")
    @Column(nullable = false)
    private Integer quantidade;

    @NotNull(message = "Preço unitário é obrigatório")
    @Column(name = "preco_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoUnitario;

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(length = 500)
    private String observacoes;

    /**
     * Calcula o subtotal do item (quantidade * preço unitário)
     */
    public void calcularSubtotal() {
        this.subtotal = precoUnitario.multiply(BigDecimal.valueOf(quantidade));
    }

    /**
     * Método chamado antes de persistir
     */
    @PrePersist
    @PreUpdate
    public void prePersist() {
        calcularSubtotal();
    }

    public BigDecimal getSubtotal() {
        return this.subtotal;
    }

    public void setPedido(Pedido pedido) {
        this.pedido = pedido;
    }

    public void setSubPedido(SubPedido subPedido) {
        this.subPedido = subPedido;
    }

    // --- Construtores ---
    public ItemPedido() {}

    public ItemPedido(Pedido pedido, SubPedido subPedido, Produto produto, Integer quantidade,
                      BigDecimal precoUnitario, BigDecimal subtotal, String observacoes) {
        this.pedido = pedido;
        this.subPedido = subPedido;
        this.produto = produto;
        this.quantidade = quantidade;
        this.precoUnitario = precoUnitario;
        this.subtotal = subtotal;
        this.observacoes = observacoes;
    }

    // --- Getters & Setters Auxiliares ---
    public Pedido getPedido() { return pedido; }
    
    public SubPedido getSubPedido() { return subPedido; }
    
    public Produto getProduto() { return produto; }
    public void setProduto(Produto produto) { this.produto = produto; }
    
    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }
    
    public BigDecimal getPrecoUnitario() { return precoUnitario; }
    public void setPrecoUnitario(BigDecimal precoUnitario) { this.precoUnitario = precoUnitario; }
    
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }

    // --- Equals & HashCode ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ItemPedido that = (ItemPedido) o;
        return java.util.Objects.equals(pedido, that.pedido) &&
               java.util.Objects.equals(subPedido, that.subPedido) &&
               java.util.Objects.equals(produto, that.produto) &&
               java.util.Objects.equals(quantidade, that.quantidade) &&
               java.util.Objects.equals(precoUnitario, that.precoUnitario) &&
               java.util.Objects.equals(subtotal, that.subtotal) &&
               java.util.Objects.equals(observacoes, that.observacoes);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(super.hashCode(), pedido, subPedido, produto, quantidade, precoUnitario, subtotal, observacoes);
    }

    // --- Builder ---
    public static ItemPedidoBuilder builder() {
        return new ItemPedidoBuilder();
    }

    public static class ItemPedidoBuilder {
        private Pedido pedido;
        private SubPedido subPedido;
        private Produto produto;
        private Integer quantidade;
        private BigDecimal precoUnitario;
        private BigDecimal subtotal;
        private String observacoes;

        ItemPedidoBuilder() {}

        public ItemPedidoBuilder pedido(Pedido pedido) {
            this.pedido = pedido;
            return this;
        }

        public ItemPedidoBuilder subPedido(SubPedido subPedido) {
            this.subPedido = subPedido;
            return this;
        }

        public ItemPedidoBuilder produto(Produto produto) {
            this.produto = produto;
            return this;
        }

        public ItemPedidoBuilder quantidade(Integer quantidade) {
            this.quantidade = quantidade;
            return this;
        }

        public ItemPedidoBuilder precoUnitario(BigDecimal precoUnitario) {
            this.precoUnitario = precoUnitario;
            return this;
        }

        public ItemPedidoBuilder subtotal(BigDecimal subtotal) {
            this.subtotal = subtotal;
            return this;
        }

        public ItemPedidoBuilder observacoes(String observacoes) {
            this.observacoes = observacoes;
            return this;
        }

        public ItemPedido build() {
            return new ItemPedido(pedido, subPedido, produto, quantidade, precoUnitario, subtotal, observacoes);
        }
    }
}
