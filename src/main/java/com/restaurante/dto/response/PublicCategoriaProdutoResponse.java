package com.restaurante.dto.response;

import java.util.List;

public class PublicCategoriaProdutoResponse {

    private Long id;
    private String nome;
    private String slug;
    private Integer ordem;
    private List<PublicProdutoResponse> produtos;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public Integer getOrdem() {
        return ordem;
    }

    public void setOrdem(Integer ordem) {
        this.ordem = ordem;
    }

    public List<PublicProdutoResponse> getProdutos() {
        return produtos;
    }

    public void setProdutos(List<PublicProdutoResponse> produtos) {
        this.produtos = produtos;
    }
}

