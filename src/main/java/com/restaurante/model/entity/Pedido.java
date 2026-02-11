package com.restaurante.model.entity;

import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.TipoPagamentoPedido;
import jakarta.persistence.*;
import lombok.*;

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
    @Index(name = "idx_pedido_unidade_consumo", columnList = "unidade_consumo_id"),
    @Index(name = "idx_pedido_status", columnList = "status"),
    @Index(name = "idx_pedido_status_financeiro", columnList = "status_financeiro"),
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
    private StatusPedido status = StatusPedido.CRIADO;

    /**
     * Status financeiro (separado do status operacional)
     * - NAO_PAGO: Aguardando pagamento (pós-pago)
     * - PAGO: Pagamento confirmado (pré-pago ou confirmado)
     * - ESTORNADO: Pedido cancelado com devolução
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_financeiro", nullable = false, length = 20)
    @Builder.Default
    private StatusFinanceiroPedido statusFinanceiro = StatusFinanceiroPedido.NAO_PAGO;

    /**
     * Tipo de pagamento do pedido
     * - PRE_PAGO: Débito automático do Fundo de Consumo
     * - POS_PAGO: Pagamento posterior (apenas GERENTE/ADMIN)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pagamento", nullable = false, length = 20)
    @Builder.Default
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
    @Builder.Default
    private List<ItemPedido> itens = new ArrayList<>();

    // Relacionamento com SubPedidos
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SubPedido> subPedidos = new ArrayList<>();

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
}
