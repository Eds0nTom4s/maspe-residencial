package com.restaurante.model.entity;

import com.restaurante.model.enums.CategoriaProduto;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidade Produto
 * Representa um item do cardápio disponível para pedidos
 */
@Entity
@Table(name = "produtos", indexes = {
    @Index(name = "idx_produto_codigo", columnList = "codigo", unique = true),
    @Index(name = "idx_produto_categoria", columnList = "categoria"),
    @Index(name = "idx_produto_ativo", columnList = "ativo")
})
public class Produto extends BaseEntity {

    @NotBlank(message = "Código é obrigatório")
    @Size(max = 50)
    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 3, max = 150, message = "Nome deve ter entre 3 e 150 caracteres")
    @Column(nullable = false, length = 150)
    private String nome;

    @Size(max = 500)
    @Column(length = 500)
    private String descricao;

    @NotNull(message = "Preço é obrigatório")
    @DecimalMin(value = "0.01", message = "Preço deve ser maior que zero")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "Categoria é obrigatória")
    @Column(nullable = false, length = 30)
    private CategoriaProduto categoria;

    @Column(name = "url_imagem", length = 500)
    private String urlImagem;

    @Column(name = "tempo_preparo_minutos")
    private Integer tempoPreparoMinutos;

    @Column(name = "disponivel", nullable = false)
    private Boolean disponivel = true;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    // Relacionamento com itens de pedido
    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ItemPedido> itensPedido = new ArrayList<>();

    /**
     * Calcula o subtotal baseado na quantidade
     */
    public BigDecimal calcularSubtotal(Integer quantidade) {
        return preco.multiply(BigDecimal.valueOf(quantidade));
    }

    public String getCodigo() { return this.codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNome() { return this.nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getDescricao() { return this.descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public BigDecimal getPreco() { return this.preco; }
    public void setPreco(BigDecimal preco) { this.preco = preco; }

    public CategoriaProduto getCategoria() { return this.categoria; }
    public void setCategoria(CategoriaProduto categoria) { this.categoria = categoria; }

    public String getUrlImagem() { return this.urlImagem; }
    public void setUrlImagem(String urlImagem) { this.urlImagem = urlImagem; }

    public Integer getTempoPreparoMinutos() { return this.tempoPreparoMinutos; }
    public void setTempoPreparoMinutos(Integer tempoPreparoMinutos) { this.tempoPreparoMinutos = tempoPreparoMinutos; }

    public Boolean getDisponivel() { return this.disponivel; }
    public void setDisponivel(Boolean disponivel) { this.disponivel = disponivel; }

    public Boolean getAtivo() { return this.ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }

    public List<ItemPedido> getItensPedido() { return this.itensPedido; }
    public void setItensPedido(List<ItemPedido> itensPedido) { this.itensPedido = itensPedido; }

    // --- Construtores ---
    public Produto() {}

    public Produto(String codigo, String nome, String descricao, BigDecimal preco,
                   CategoriaProduto categoria, String urlImagem, Integer tempoPreparoMinutos,
                   Boolean disponivel, Boolean ativo, List<ItemPedido> itensPedido) {
        this.codigo = codigo;
        this.nome = nome;
        this.descricao = descricao;
        this.preco = preco;
        this.categoria = categoria;
        this.urlImagem = urlImagem;
        this.tempoPreparoMinutos = tempoPreparoMinutos;
        this.disponivel = disponivel != null ? disponivel : true;
        this.ativo = ativo != null ? ativo : true;
        this.itensPedido = itensPedido != null ? itensPedido : new ArrayList<>();
    }

    // --- Equals & HashCode ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Produto produto = (Produto) o;
        return java.util.Objects.equals(codigo, produto.codigo);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(super.hashCode(), codigo);
    }

    // --- Builder ---
    public static ProdutoBuilder builder() {
        return new ProdutoBuilder();
    }

    public static class ProdutoBuilder {
        private String codigo;
        private String nome;
        private String descricao;
        private BigDecimal preco;
        private CategoriaProduto categoria;
        private String urlImagem;
        private Integer tempoPreparoMinutos;
        private Boolean disponivel;
        private Boolean ativo;
        private List<ItemPedido> itensPedido;

        ProdutoBuilder() {}

        public ProdutoBuilder codigo(String codigo) {
            this.codigo = codigo;
            return this;
        }

        public ProdutoBuilder nome(String nome) {
            this.nome = nome;
            return this;
        }

        public ProdutoBuilder descricao(String descricao) {
            this.descricao = descricao;
            return this;
        }

        public ProdutoBuilder preco(BigDecimal preco) {
            this.preco = preco;
            return this;
        }

        public ProdutoBuilder categoria(CategoriaProduto categoria) {
            this.categoria = categoria;
            return this;
        }

        public ProdutoBuilder urlImagem(String urlImagem) {
            this.urlImagem = urlImagem;
            return this;
        }

        public ProdutoBuilder tempoPreparoMinutos(Integer tempoPreparoMinutos) {
            this.tempoPreparoMinutos = tempoPreparoMinutos;
            return this;
        }

        public ProdutoBuilder disponivel(Boolean disponivel) {
            this.disponivel = disponivel;
            return this;
        }

        public ProdutoBuilder ativo(Boolean ativo) {
            this.ativo = ativo;
            return this;
        }

        public ProdutoBuilder itensPedido(List<ItemPedido> itensPedido) {
            this.itensPedido = itensPedido;
            return this;
        }

        public Produto build() {
            return new Produto(codigo, nome, descricao, preco, categoria, urlImagem,
                               tempoPreparoMinutos, disponivel, ativo, itensPedido);
        }
    }
}
