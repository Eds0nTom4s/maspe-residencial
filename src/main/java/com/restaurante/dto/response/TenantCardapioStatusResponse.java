package com.restaurante.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public class TenantCardapioStatusResponse {

    private Long tenantId;
    private Boolean publicado;
    private LocalDateTime publicadoEm;
    private Long publicadoPorUserId;
    private LocalDateTime despublicadoEm;
    private Long despublicadoPorUserId;
    private String motivoDespublicacao;
    private Integer maxCategorias;
    private Integer maxProdutos;
    private Long categoriasAtuais;
    private Long produtosAtuais;
    private Long produtosDisponiveis;
    private Boolean podePublicar;
    private List<String> bloqueios;
    private List<String> avisos;
    private String telefoneContato;
    private String urlBanner;
    private Integer maxItensPorPedido;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Boolean getPublicado() { return publicado; }
    public void setPublicado(Boolean publicado) { this.publicado = publicado; }
    public LocalDateTime getPublicadoEm() { return publicadoEm; }
    public void setPublicadoEm(LocalDateTime publicadoEm) { this.publicadoEm = publicadoEm; }
    public Long getPublicadoPorUserId() { return publicadoPorUserId; }
    public void setPublicadoPorUserId(Long publicadoPorUserId) { this.publicadoPorUserId = publicadoPorUserId; }
    public LocalDateTime getDespublicadoEm() { return despublicadoEm; }
    public void setDespublicadoEm(LocalDateTime despublicadoEm) { this.despublicadoEm = despublicadoEm; }
    public Long getDespublicadoPorUserId() { return despublicadoPorUserId; }
    public void setDespublicadoPorUserId(Long despublicadoPorUserId) { this.despublicadoPorUserId = despublicadoPorUserId; }
    public String getMotivoDespublicacao() { return motivoDespublicacao; }
    public void setMotivoDespublicacao(String motivoDespublicacao) { this.motivoDespublicacao = motivoDespublicacao; }
    public Integer getMaxCategorias() { return maxCategorias; }
    public void setMaxCategorias(Integer maxCategorias) { this.maxCategorias = maxCategorias; }
    public Integer getMaxProdutos() { return maxProdutos; }
    public void setMaxProdutos(Integer maxProdutos) { this.maxProdutos = maxProdutos; }
    public Long getCategoriasAtuais() { return categoriasAtuais; }
    public void setCategoriasAtuais(Long categoriasAtuais) { this.categoriasAtuais = categoriasAtuais; }
    public Long getProdutosAtuais() { return produtosAtuais; }
    public void setProdutosAtuais(Long produtosAtuais) { this.produtosAtuais = produtosAtuais; }
    public Long getProdutosDisponiveis() { return produtosDisponiveis; }
    public void setProdutosDisponiveis(Long produtosDisponiveis) { this.produtosDisponiveis = produtosDisponiveis; }
    public Boolean getPodePublicar() { return podePublicar; }
    public void setPodePublicar(Boolean podePublicar) { this.podePublicar = podePublicar; }
    public List<String> getBloqueios() { return bloqueios; }
    public void setBloqueios(List<String> bloqueios) { this.bloqueios = bloqueios; }
    public List<String> getAvisos() { return avisos; }
    public void setAvisos(List<String> avisos) { this.avisos = avisos; }
    public String getTelefoneContato() { return telefoneContato; }
    public void setTelefoneContato(String telefoneContato) { this.telefoneContato = telefoneContato; }
    public String getUrlBanner() { return urlBanner; }
    public void setUrlBanner(String urlBanner) { this.urlBanner = urlBanner; }
    public Integer getMaxItensPorPedido() { return maxItensPorPedido; }
    public void setMaxItensPorPedido(Integer maxItensPorPedido) { this.maxItensPorPedido = maxItensPorPedido; }
}
