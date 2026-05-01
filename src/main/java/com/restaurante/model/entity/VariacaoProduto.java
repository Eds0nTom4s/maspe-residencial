package com.restaurante.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * VariacaoProduto — Variações de produto para a Loja do Sócio.
 *
 * <p>Suporta variações simples como Tamanho (S/M/L/XL) e Cor (Vermelho/Azul).
 * Campo {@code stock} é nullable no MVP — sem gestão de inventário nesta fase.
 *
 * <p>Uso: Camisolas e equipamentos desportivos do Sagrada Esperança.
 */
@Entity
@Table(name = "variacoes_produto", indexes = {
    @Index(name = "idx_variacao_produto", columnList = "produto_id"),
    @Index(name = "idx_variacao_tipo", columnList = "tipo"),
    @Index(name = "idx_variacao_ativo", columnList = "ativo"),
    @Index(name = "idx_variacao_sku", columnList = "sku", unique = true)
})
public class VariacaoProduto extends BaseEntity {

    /**
     * Produto ao qual esta variação pertence.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    @NotNull
    private Produto produto;

    /**
     * Tipo da variação: TAMANHO, COR ou OUTRO.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @NotNull
    private TipoVariacao tipo;

    /**
     * Valor legível da variação.
     * Exemplos: "S", "M", "L", "XL", "Vermelho", "Azul", "36".
     */
    @NotBlank(message = "Valor da variação é obrigatório")
    @Column(nullable = false, length = 50)
    private String valor;

    @Column(length = 30)
    private String tamanho;

    @Column(length = 50)
    private String cor;

    @Column(unique = true, length = 80)
    private String sku;

    @Column(precision = 10, scale = 2)
    private BigDecimal preco;

    /**
     * Stock disponível (nullable no MVP — sem gestão de inventário).
     */
    @Column(name = "stock")
    private Integer stock;

    /**
     * Variação activa e disponível para selecção.
     */
    @Column(nullable = false)
    private Boolean ativo = true;

    // ── Enum interno ────────────────────────────────────────────────────────

    public enum TipoVariacao {
        TAMANHO, COR, OUTRO
    }

    // ── Construtores ────────────────────────────────────────────────────────

    public VariacaoProduto() {}

    public VariacaoProduto(Produto produto, TipoVariacao tipo, String valor, Integer stock, Boolean ativo) {
        this(produto, tipo, valor, null, null, null, null, stock, ativo);
    }

    public VariacaoProduto(Produto produto, TipoVariacao tipo, String valor, String tamanho,
                           String cor, String sku, BigDecimal preco, Integer stock, Boolean ativo) {
        this.produto = produto;
        this.tipo = tipo;
        this.valor = valor;
        this.tamanho = tamanho;
        this.cor = cor;
        this.sku = sku;
        this.preco = preco;
        this.stock = stock;
        this.ativo = ativo != null ? ativo : true;
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public Produto getProduto() { return produto; }
    public void setProduto(Produto produto) { this.produto = produto; }

    public TipoVariacao getTipo() { return tipo; }
    public void setTipo(TipoVariacao tipo) { this.tipo = tipo; }

    public String getValor() { return valor; }
    public void setValor(String valor) { this.valor = valor; }

    public String getTamanho() { return tamanho; }
    public void setTamanho(String tamanho) { this.tamanho = tamanho; }

    public String getCor() { return cor; }
    public void setCor(String cor) { this.cor = cor; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public BigDecimal getPreco() { return preco; }
    public void setPreco(BigDecimal preco) { this.preco = preco; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }

    // ── equals / hashCode ────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VariacaoProduto)) return false;
        if (!super.equals(o)) return false;
        VariacaoProduto that = (VariacaoProduto) o;
        return Objects.equals(produto, that.produto) && tipo == that.tipo && Objects.equals(valor, that.valor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), produto, tipo, valor);
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Produto produto;
        private TipoVariacao tipo;
        private String valor;
        private String tamanho;
        private String cor;
        private String sku;
        private BigDecimal preco;
        private Integer stock;
        private Boolean ativo = true;

        public Builder produto(Produto produto) { this.produto = produto; return this; }
        public Builder tipo(TipoVariacao tipo) { this.tipo = tipo; return this; }
        public Builder valor(String valor) { this.valor = valor; return this; }
        public Builder tamanho(String tamanho) { this.tamanho = tamanho; return this; }
        public Builder cor(String cor) { this.cor = cor; return this; }
        public Builder sku(String sku) { this.sku = sku; return this; }
        public Builder preco(BigDecimal preco) { this.preco = preco; return this; }
        public Builder stock(Integer stock) { this.stock = stock; return this; }
        public Builder ativo(Boolean ativo) { this.ativo = ativo; return this; }

        public VariacaoProduto build() {
            return new VariacaoProduto(produto, tipo, valor, tamanho, cor, sku, preco, stock, ativo);
        }
    }
}
