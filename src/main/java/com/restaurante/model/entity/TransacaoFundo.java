package com.restaurante.model.entity;

import com.restaurante.model.enums.TipoTransacaoFundo;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

/**
 * Transação em Fundo de Consumo
 * 
 * AUDITORIA FINANCEIRA:
 * - Registro imutável de toda movimentação
 * - CREDITO: recarga de saldo
 * - DEBITO: pagamento de pedido
 * - ESTORNO: devolução por cancelamento
 * 
 * Relacionamento com Pedido:
 * - DEBITO e ESTORNO vinculam pedidoId
 * - CREDITO não vincula pedido (recarga manual)
 */
@Entity
@Table(name = "transacoes_fundo", indexes = {
    @Index(name = "idx_transacao_fundo", columnList = "fundo_consumo_id"),
    @Index(name = "idx_transacao_pedido", columnList = "pedido_id"),
    @Index(name = "idx_transacao_tipo", columnList = "tipo")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransacaoFundo extends BaseEntity {

    /**
     * Fundo de Consumo relacionado
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fundo_consumo_id", nullable = false)
    @NotNull
    private FundoConsumo fundoConsumo;

    /**
     * Valor da transação (sempre positivo)
     */
    @Column(nullable = false, precision = 10, scale = 2)
    @NotNull
    private BigDecimal valor;

    /**
     * Tipo: CREDITO, DEBITO, ESTORNO
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @NotNull
    private TipoTransacaoFundo tipo;

    /**
     * Pedido relacionado (nullable)
     * DEBITO e ESTORNO vinculam pedido
     * CREDITO não vincula (recarga manual)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    /**
     * Saldo ANTES da transação
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal saldoAnterior;

    /**
     * Saldo DEPOIS da transação
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal saldoNovo;

    /**
     * Observações/motivo
     */
    @Column(length = 500)
    private String observacoes;
}
