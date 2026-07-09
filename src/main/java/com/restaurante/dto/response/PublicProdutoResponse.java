package com.restaurante.dto.response;

import java.math.BigDecimal;
import java.util.List;

public class PublicProdutoResponse {

    private Long id;
    private String codigo;
    private String nome;
    private String descricao;
    private BigDecimal preco;
    private String imagemUrl;
    private List<PublicProdutoImagemResponse> imagens;
    private Boolean disponivel;
    private Long categoriaProdutoId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public void setPreco(BigDecimal preco) {
        this.preco = preco;
    }

    public String getImagemUrl() {
        return imagemUrl;
    }

    public void setImagemUrl(String imagemUrl) {
        this.imagemUrl = imagemUrl;
    }

    public List<PublicProdutoImagemResponse> getImagens() {
        return imagens;
    }

    public void setImagens(List<PublicProdutoImagemResponse> imagens) {
        this.imagens = imagens;
    }

    public Boolean getDisponivel() {
        return disponivel;
    }

    public void setDisponivel(Boolean disponivel) {
        this.disponivel = disponivel;
    }

    public Long getCategoriaProdutoId() {
        return categoriaProdutoId;
    }

    public void setCategoriaProdutoId(Long categoriaProdutoId) {
        this.categoriaProdutoId = categoriaProdutoId;
    }
}

