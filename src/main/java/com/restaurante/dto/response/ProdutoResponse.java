package com.restaurante.dto.response;

import com.restaurante.model.enums.CategoriaProduto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public class ProdutoResponse {

    private Long id;
    private String codigo;
    private String nome;
    private String descricao;
    private BigDecimal preco;
    private CategoriaProduto categoria;
    private String urlImagem;
    private Integer tempoPreparoMinutos;
    private Boolean disponivel;
    private Boolean ativo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;



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

    public CategoriaProduto getCategoria() { return categoria; }
    public void setCategoria(CategoriaProduto categoria) { this.categoria = categoria; }

    public String getUrlImagem() { return urlImagem; }
    public void setUrlImagem(String urlImagem) { this.urlImagem = urlImagem; }

    public Integer getTempoPreparoMinutos() { return tempoPreparoMinutos; }
    public void setTempoPreparoMinutos(Integer tempoPreparoMinutos) { this.tempoPreparoMinutos = tempoPreparoMinutos; }

    public Boolean getDisponivel() { return disponivel; }
    public void setDisponivel(Boolean disponivel) { this.disponivel = disponivel; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public static ProdutoResponseBuilder builder() {
        return new ProdutoResponseBuilder();
    }

    public static class ProdutoResponseBuilder {
        private Long id;
        private String codigo;
        private String nome;
        private String descricao;
        private BigDecimal preco;
        private CategoriaProduto categoria;
        private String urlImagem;
        private Integer tempoPreparoMinutos;
        private Boolean disponivel;
        private Boolean ativo;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public ProdutoResponseBuilder id(Long id) { this.id = id; return this; }
        public ProdutoResponseBuilder codigo(String codigo) { this.codigo = codigo; return this; }
        public ProdutoResponseBuilder nome(String nome) { this.nome = nome; return this; }
        public ProdutoResponseBuilder descricao(String descricao) { this.descricao = descricao; return this; }
        public ProdutoResponseBuilder preco(BigDecimal preco) { this.preco = preco; return this; }
        public ProdutoResponseBuilder categoria(CategoriaProduto categoria) { this.categoria = categoria; return this; }
        public ProdutoResponseBuilder urlImagem(String urlImagem) { this.urlImagem = urlImagem; return this; }
        public ProdutoResponseBuilder tempoPreparoMinutos(Integer tempoPreparoMinutos) { this.tempoPreparoMinutos = tempoPreparoMinutos; return this; }
        public ProdutoResponseBuilder disponivel(Boolean disponivel) { this.disponivel = disponivel; return this; }
        public ProdutoResponseBuilder ativo(Boolean ativo) { this.ativo = ativo; return this; }
        public ProdutoResponseBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public ProdutoResponseBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public ProdutoResponse build() {
            ProdutoResponse response = new ProdutoResponse();
            response.setId(id);
            response.setCodigo(codigo);
            response.setNome(nome);
            response.setDescricao(descricao);
            response.setPreco(preco);
            response.setCategoria(categoria);
            response.setUrlImagem(urlImagem);
            response.setTempoPreparoMinutos(tempoPreparoMinutos);
            response.setDisponivel(disponivel);
            response.setAtivo(ativo);
            response.setCreatedAt(createdAt);
            response.setUpdatedAt(updatedAt);
            return response;
        }
    }
}
