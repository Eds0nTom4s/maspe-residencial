package com.restaurante.model.entity;

import com.restaurante.model.enums.TipoTransacaoFundo;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

import java.math.BigDecimal;

/**
 * Registro IMUTÁVEL de uma movimentação financeira no Fundo de Consumo.
 *
 * AUDITORIA FINANCEIRA (append-only):
 * - Uma vez criada, uma TransacaoFundo NUNCA deve ser modificada.
 * - Não expõe setters públicos — design intencional.
 * - O saldo actual é sempre a SOMA das transacções, nunca um campo mutável.
 *
 * Tipos:
 * - CREDITO: recarga de saldo (ex: pagamento via AppyPay)
 * - DEBITO:  pagamento de pedido (descontado automaticamente)
 * - ESTORNO: devolução por cancelamento
 * - AJUSTE:  correcção administrativa (apenas ADMIN)
 *
 * Idempotência:
 * - {@code merchantTransactionId} garante que a mesma operação externa
 *   não é processada duas vezes.
 *
 * Relacionamento com Pedido:
 * - DEBITO e ESTORNO vinculam pedidoId.
 * - CREDITO não vincula pedido (recarga manual/gateway).
 */
@Entity
@Table(name = "transacoes_fundo", indexes = {
    @Index(name = "idx_transacao_fundo",       columnList = "fundo_consumo_id"),
    @Index(name = "idx_transacao_pedido",       columnList = "pedido_id"),
    @Index(name = "idx_transacao_tipo",         columnList = "tipo"),
    @Index(name = "idx_transacao_merchant_id",  columnList = "merchant_transaction_id")  // IC-3 / IM-2
})
public class TransacaoFundo extends BaseEntity {

    /**
     * Fundo de Consumo relacionado.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fundo_consumo_id", nullable = false)
    @NotNull
    private FundoConsumo fundoConsumo;

    /**
     * Valor da transação (sempre positivo).
     */
    @Column(nullable = false, precision = 19, scale = 2)
    @NotNull
    private BigDecimal valor;

    /**
     * Tipo: CREDITO, DEBITO, ESTORNO, AJUSTE.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @NotNull
    private TipoTransacaoFundo tipo;

    /**
     * Pedido relacionado (nullable).
     * DEBITO e ESTORNO vinculam pedido. CREDITO não vincula.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    /**
     * Saldo ANTES da transação (snapshot auditável).
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoAnterior;

    /**
     * Saldo DEPOIS da transação (snapshot auditável).
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoNovo;

    /**
     * ID da transação no Gateway de Pagamento (ex: AppyPay).
     * Garante a idempotência de operações externas.
     * ÚNICO na tabela.
     */
    @Column(name = "merchant_transaction_id", unique = true, length = 100)
    private String merchantTransactionId;

    /**
     * Observações / motivo da transação.
     */
    @Column(length = 500)
    private String observacoes;

    // ── Constructors ──────────────────────────────────────────────────────────
    // Construtor JPA obrigatório (package-private para desencorajar uso directo)
    protected TransacaoFundo() {}

    /**
     * Construtor completo — usada pelo Builder.
     * Não exposta como pública para forçar uso do Builder.
     */
    private TransacaoFundo(FundoConsumo fundoConsumo, BigDecimal valor, TipoTransacaoFundo tipo,
                           Pedido pedido, BigDecimal saldoAnterior, BigDecimal saldoNovo,
                           String merchantTransactionId, String observacoes) {
        this.fundoConsumo = fundoConsumo;
        this.valor = valor;
        this.tipo = tipo;
        this.pedido = pedido;
        this.saldoAnterior = saldoAnterior;
        this.saldoNovo = saldoNovo;
        this.merchantTransactionId = merchantTransactionId;
        this.observacoes = observacoes;
    }

    // ── Getters ONLY — sem setters (imutável após criação) ────────────────────
    public FundoConsumo getFundoConsumo()       { return fundoConsumo; }
    public BigDecimal getValor()                { return valor; }
    public TipoTransacaoFundo getTipo()         { return tipo; }
    public Pedido getPedido()                   { return pedido; }
    public BigDecimal getSaldoAnterior()        { return saldoAnterior; }
    public BigDecimal getSaldoNovo()            { return saldoNovo; }
    public String getMerchantTransactionId()    { return merchantTransactionId; }
    public String getObservacoes()              { return observacoes; }

    // ── equals / hashCode ─────────────────────────────────────────────────────
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        TransacaoFundo that = (TransacaoFundo) o;
        // Identidade baseada em merchantTransactionId quando presente;
        // caso contrário, herda identidade da BaseEntity (ID da DB).
        if (this.merchantTransactionId != null) {
            return Objects.equals(merchantTransactionId, that.merchantTransactionId);
        }
        return Objects.equals(fundoConsumo, that.fundoConsumo) &&
               Objects.equals(valor, that.valor) &&
               tipo == that.tipo &&
               Objects.equals(saldoAnterior, that.saldoAnterior) &&
               Objects.equals(saldoNovo, that.saldoNovo);
    }

    @Override
    public int hashCode() {
        return merchantTransactionId != null
            ? Objects.hash(super.hashCode(), merchantTransactionId)
            : Objects.hash(super.hashCode(), fundoConsumo, valor, tipo, saldoAnterior, saldoNovo);
    }

    // ── Builder ───────────────────────────────────────────────────────────────
    public static TransacaoFundoBuilder builder() { return new TransacaoFundoBuilder(); }

    public static class TransacaoFundoBuilder {
        private FundoConsumo fundoConsumo;
        private BigDecimal valor;
        private TipoTransacaoFundo tipo;
        private Pedido pedido;
        private BigDecimal saldoAnterior;
        private BigDecimal saldoNovo;
        private String merchantTransactionId;
        private String observacoes;

        TransacaoFundoBuilder() {}

        public TransacaoFundoBuilder fundoConsumo(FundoConsumo fundoConsumo)            { this.fundoConsumo = fundoConsumo; return this; }
        public TransacaoFundoBuilder valor(BigDecimal valor)                            { this.valor = valor; return this; }
        public TransacaoFundoBuilder tipo(TipoTransacaoFundo tipo)                      { this.tipo = tipo; return this; }
        public TransacaoFundoBuilder pedido(Pedido pedido)                              { this.pedido = pedido; return this; }
        public TransacaoFundoBuilder saldoAnterior(BigDecimal saldoAnterior)            { this.saldoAnterior = saldoAnterior; return this; }
        public TransacaoFundoBuilder saldoNovo(BigDecimal saldoNovo)                    { this.saldoNovo = saldoNovo; return this; }
        public TransacaoFundoBuilder merchantTransactionId(String merchantTransactionId){ this.merchantTransactionId = merchantTransactionId; return this; }
        public TransacaoFundoBuilder observacoes(String observacoes)                    { this.observacoes = observacoes; return this; }

        public TransacaoFundo build() {
            Objects.requireNonNull(fundoConsumo, "fundoConsumo é obrigatório");
            Objects.requireNonNull(valor,        "valor é obrigatório");
            Objects.requireNonNull(tipo,         "tipo é obrigatório");
            Objects.requireNonNull(saldoAnterior,"saldoAnterior é obrigatório");
            Objects.requireNonNull(saldoNovo,    "saldoNovo é obrigatório");
            if (valor.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Valor da transação deve ser positivo: " + valor);
            }
            return new TransacaoFundo(fundoConsumo, valor, tipo, pedido,
                                      saldoAnterior, saldoNovo, merchantTransactionId, observacoes);
        }
    }
}
