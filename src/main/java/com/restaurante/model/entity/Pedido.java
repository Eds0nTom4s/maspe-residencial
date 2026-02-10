package com.restaurante.model.entity;

import com.restaurante.model.enums.StatusPedido;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidade Pedido
 * Representa um pedido feito por um cliente em uma mesa
 * Cada pedido contém múltiplos itens (produtos)
 */
@Entity
@Table(name = "pedidos", indexes = {
    @Index(name = "idx_pedido_unidade_consumo", columnList = "unidade_consumo_id"),
    @Index(name = "idx_pedido_status", columnList = "status"),
    @Index(name = "idx_pedido_numero", columnList = "numero")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pedido extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String numero; // Ex: PED-20260208-001

    // Relacionamento OBRIGATÓRIO com UnidadeDeConsumo
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_consumo_id", nullable = false)
    private UnidadeDeConsumo unidadeConsumo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusPedido status = StatusPedido.PENDENTE;

    @Column(length = 500)
    private String observacoes;

    // Relacionamento com itens do pedido
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ItemPedido> itens = new ArrayList<>();

    @Column(name = "total", precision = 10, scale = 2)
    private BigDecimal total;

    /**
     * Calcula o total do pedido somando todos os itens
     */
    public BigDecimal calcularTotal() {
        BigDecimal totalCalculado = itens.stream()
            .map(ItemPedido::getSubtotal)
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
        return status == StatusPedido.PENDENTE || status == StatusPedido.RECEBIDO;
    }

    /**
     * Avança o status do pedido
     */
    public void avancarStatus() {
        switch (status) {
            case PENDENTE -> status = StatusPedido.RECEBIDO;
            case RECEBIDO -> status = StatusPedido.EM_PREPARO;
            case EM_PREPARO -> status = StatusPedido.PRONTO;
            case PRONTO -> status = StatusPedido.ENTREGUE;
        }
    }
}
