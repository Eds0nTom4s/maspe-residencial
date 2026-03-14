package com.restaurante.dto.response;

import java.math.BigDecimal;

/**
 * DTO para resposta de produtos mais vendidos do dashboard
 */
public class DashboardTopProductResponse {
    private Long produtoId;
    private String nome;
    private Integer quantidadeVendida;
    private BigDecimal valorTotal;
    private String categoria;

    public DashboardTopProductResponse() {}

    public DashboardTopProductResponse(Long produtoId, String nome, Integer quantidadeVendida,
                                       BigDecimal valorTotal, String categoria) {
        this.produtoId = produtoId;
        this.nome = nome;
        this.quantidadeVendida = quantidadeVendida;
        this.valorTotal = valorTotal;
        this.categoria = categoria;
    }

    public Long getProdutoId() { return produtoId; }
    public void setProdutoId(long produtoId) { this.produtoId = produtoId; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public Integer getQuantidadeVendida() { return quantidadeVendida; }
    public void setQuantidadeVendida(int quantidadeVendida) { this.quantidadeVendida = quantidadeVendida; }
    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
}
