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
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemPedido extends BaseEntity {

    // Relacionamento OBRIGATÓRIO com Pedido (mantido para compatibilidade)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id")
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
}
