package com.restaurante.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Fundo de Consumo (Pré-Pago)
 *
 * CONCEITO:
 * Cada SessaoConsumo possui exactamente um FundoConsumo.
 * O fundo representa o saldo financeiro associado à sessão.
 * Pedidos debitam automaticamente do fundo.
 *
 * REGRAS:
 * - Cada sessão possui UM único fundo (criado automaticamente na abertura)
 * - Saldo não pode ficar negativo
 * - Toda movimentação gera TransacaoFundo auditável
 * - Concorrência protegida com @Version
 * - O QR Code de acesso ao fundo é o qrCodeSessao da SessaoConsumo
 */
@Entity
@Table(name = "fundos_consumo", indexes = {
    @Index(name = "idx_fundo_sessao", columnList = "sessao_consumo_id"),
    @Index(name = "idx_fundo_status", columnList = "ativo"),
    @Index(name = "idx_fundo_sessao_ativo", columnList = "sessao_consumo_id, ativo")
})
public class FundoConsumo extends BaseEntity {

    /**
     * Sessão de consumo dona deste fundo.
     *
     * <p>Relação 1:1 obrigatória. O fundo é criado automaticamente ao abrir
     * a sessão e encerrado ao encerrar a sessão.
     * Acesso externo ao fundo é feito via {@code SessaoConsumo.qrCodeSessao}.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_consumo_id", nullable = false, unique = true)
    private SessaoConsumo sessaoConsumo;

    /**
     * Saldo refletido da soma de transações.
     * Na base de dados anterior este campo era mutável.
     * Agora passa a ser um derivado ou pelo menos inserido com cuidado.
     * O ideal no append-only é que seja descartável, mas manteremos como persistido
     * se a aplicação precisar para query rápido, APENAS atualizado na trigger/Service após transação.
     * Sempre >= 0 no modelo Pré-Pago.
     */
    @Column(name = "saldo_atual", precision = 10, scale = 2)
    private BigDecimal saldoAtual = BigDecimal.ZERO;

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
    private List<TransacaoFundo> transacoes = new ArrayList<>();

    // NOTA: Optimistic Locking (@Version) é herdado de BaseEntity.
    // NÃO declarar @Version aqui — campo duplicado causaria comportamento imprevisível no JPA.
    
    /**
     * Campo transiente para facilitar leitura do saldo quando carregado pelo repositório.
     * Não é persistido nativamente como uma coluna mutável (Append-Only design).
     */
    // Métodos mutadores de saldo (creditar/debitar) removidos para garantir que o saldo
    // seja estritamente derivado da inserção de TransacaoFundo (Append-Only).

    /**
     * Encerra o fundo (não permite mais movimentação).
     */
    public void encerrar() {
        this.ativo = false;
    }

    public FundoConsumo() {}

    public FundoConsumo(SessaoConsumo sessaoConsumo, BigDecimal saldoAtual, Boolean ativo,
                        List<TransacaoFundo> transacoes) {
        this.sessaoConsumo = sessaoConsumo;
        this.saldoAtual = saldoAtual != null ? saldoAtual : BigDecimal.ZERO;
        this.ativo = ativo;
        this.transacoes = transacoes != null ? transacoes : new ArrayList<>();
    }

    public SessaoConsumo getSessaoConsumo() { return sessaoConsumo; }
    public void setSessaoConsumo(SessaoConsumo sessaoConsumo) { this.sessaoConsumo = sessaoConsumo; }

    public BigDecimal getSaldoAtual() { return saldoAtual; }
    // setSaldoAtual() removido intencionalmente — saldo só deve ser alterado via atualizarSaldoCache(),
    // chamado pelo FundoConsumoService após inserção de TransacaoFundo (ledger append-only).

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }

    public List<TransacaoFundo> getTransacoes() { return transacoes; }
    public void setTransacoes(List<TransacaoFundo> transacoes) { this.transacoes = transacoes; }

    public boolean temSaldoSuficiente(BigDecimal valor) {
        return this.saldoAtual != null && this.saldoAtual.compareTo(valor) >= 0;
    }

    /**
     * Actualiza o saldo persistido (campo cache normalizado).
     * Chamado pelo FundoConsumoService após cada transação.
     */
    public void atualizarSaldoCache(BigDecimal saldo) {
        this.saldoAtual = saldo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        FundoConsumo that = (FundoConsumo) o;
        // Identidade baseada na sessão (relação 1:1 única)
        return Objects.equals(sessaoConsumo, that.sessaoConsumo) &&
               Objects.equals(ativo, that.ativo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), sessaoConsumo, ativo);
    }

    public static FundoConsumoBuilder builder() {
        return new FundoConsumoBuilder();
    }

    public static class FundoConsumoBuilder {
        private SessaoConsumo sessaoConsumo;
        private BigDecimal saldoAtual;
        private Boolean ativo;
        private List<TransacaoFundo> transacoes;

        FundoConsumoBuilder() {}

        public FundoConsumoBuilder sessaoConsumo(SessaoConsumo sessaoConsumo) {
            this.sessaoConsumo = sessaoConsumo;
            return this;
        }

        public FundoConsumoBuilder saldoAtual(BigDecimal saldoAtual) {
            this.saldoAtual = saldoAtual;
            return this;
        }

        public FundoConsumoBuilder ativo(Boolean ativo) {
            this.ativo = ativo;
            return this;
        }

        public FundoConsumoBuilder transacoes(List<TransacaoFundo> transacoes) {
            this.transacoes = transacoes;
            return this;
        }

        public FundoConsumo build() {
            return new FundoConsumo(this.sessaoConsumo, this.saldoAtual, this.ativo, this.transacoes);
        }
    }
}
