package com.restaurante.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Fundo de Consumo (Pré-Pago)
 * 
 * CONCEITO:
 * Cliente carrega saldo antecipadamente
 * Pedidos debitam automaticamente
 * Evita pós-pago desnecessário
 * 
 * REGRAS:
 * - Um cliente tem UM fundo ativo por vez
 * - Saldo não pode ficar negativo
 * - Toda movimentação gera TransacaoFundo
 * - Concorrência protegida com @Version
 */
@Entity
@Table(name = "fundos_consumo", indexes = {
    @Index(name = "idx_fundo_cliente", columnList = "cliente_id"),
    @Index(name = "idx_fundo_status", columnList = "ativo")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FundoConsumo extends BaseEntity {

    /**
     * Cliente proprietário do fundo
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false, unique = true)
    @NotNull
    private Cliente cliente;

    /**
     * Saldo atual disponível
     * Sempre >= 0
     */
    @Column(nullable = false, precision = 10, scale = 2)
    @NotNull
    private BigDecimal saldoAtual;

    /**
     * Fundo ativo?
     * false = encerrado (não permite movimentação)
     */
    @Column(nullable = false)
    @NotNull
    private Boolean ativo;

    /**
     * Histórico de transações
     */
    @OneToMany(mappedBy = "fundoConsumo", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TransacaoFundo> transacoes = new ArrayList<>();

    /**
     * Verifica se tem saldo suficiente
     */
    public boolean temSaldoSuficiente(BigDecimal valor) {
        return saldoAtual.compareTo(valor) >= 0;
    }

    /**
     * Debita valor do saldo
     * NÃO valida saldo - validação é feita no Service
     */
    public void debitar(BigDecimal valor) {
        this.saldoAtual = this.saldoAtual.subtract(valor);
    }

    /**
     * Credita valor no saldo
     */
    public void creditar(BigDecimal valor) {
        this.saldoAtual = this.saldoAtual.add(valor);
    }

    /**
     * Encerra o fundo (não permite mais movimentação)
     */
    public void encerrar() {
        this.ativo = false;
    }
}
