package com.restaurante.store.dto;

import java.math.BigDecimal;
import java.util.List;

public class StoreProductDTO {
    private Long id;
    private String codigo;
    private String nome;
    private String descricao;
    private BigDecimal preco;
    private String categoria;
    private String urlImagem;
    private List<String> imagensGaleria;
    private Boolean disponivel;
    private List<StoreProductVariationDTO> variacoes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public BigDecimal getPreco() { return preco; }
    public void setPreco(BigDecimal preco) { this.preco = preco; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getUrlImagem() { return urlImagem; }
    public void setUrlImagem(String urlImagem) { this.urlImagem = urlImagem; }
    public List<String> getImagensGaleria() { return imagensGaleria; }
    public void setImagensGaleria(List<String> imagensGaleria) { this.imagensGaleria = imagensGaleria; }
    public Boolean getDisponivel() { return disponivel; }
    public void setDisponivel(Boolean disponivel) { this.disponivel = disponivel; }
    public List<StoreProductVariationDTO> getVariacoes() { return variacoes; }
    public void setVariacoes(List<StoreProductVariationDTO> variacoes) { this.variacoes = variacoes; }
}
