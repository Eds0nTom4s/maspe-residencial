package com.restaurante.store.dto;

import java.math.BigDecimal;

public class StoreProductVariationDTO {
    private Long id;
    private String tamanho;
    private String cor;
    private String sku;
    private BigDecimal preco;
    private Integer stock;
    private Boolean ativo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
}
