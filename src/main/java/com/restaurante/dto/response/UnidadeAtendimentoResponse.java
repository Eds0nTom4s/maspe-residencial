package com.restaurante.dto.response;

import com.restaurante.model.enums.TipoUnidadeAtendimento;

import java.util.List;

public class UnidadeAtendimentoResponse {

    private Long id;
    private String nome;
    private TipoUnidadeAtendimento tipo;
    private String descricao;
    private Boolean ativa;
    private Boolean operacional;
    private List<CozinhaResponse> cozinhas;
    private Long unidadesConsumoAtivas;

    public UnidadeAtendimentoResponse() {}

    public UnidadeAtendimentoResponse(Long id, String nome, TipoUnidadeAtendimento tipo, String descricao,
                                       Boolean ativa, Boolean operacional, List<CozinhaResponse> cozinhas,
                                       Long unidadesConsumoAtivas) {
        this.id = id;
        this.nome = nome;
        this.tipo = tipo;
        this.descricao = descricao;
        this.ativa = ativa;
        this.operacional = operacional;
        this.cozinhas = cozinhas;
        this.unidadesConsumoAtivas = unidadesConsumoAtivas;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public TipoUnidadeAtendimento getTipo() { return tipo; }
    public void setTipo(TipoUnidadeAtendimento tipo) { this.tipo = tipo; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public Boolean getAtiva() { return ativa; }
    public void setAtiva(Boolean ativa) { this.ativa = ativa; }
    public Boolean getOperacional() { return operacional; }
    public void setOperacional(Boolean operacional) { this.operacional = operacional; }
    public List<CozinhaResponse> getCozinhas() { return cozinhas; }
    public void setCozinhas(List<CozinhaResponse> cozinhas) { this.cozinhas = cozinhas; }
    public Long getUnidadesConsumoAtivas() { return unidadesConsumoAtivas; }
    public void setUnidadesConsumoAtivas(Long unidadesConsumoAtivas) { this.unidadesConsumoAtivas = unidadesConsumoAtivas; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long id;
        private String nome;
        private TipoUnidadeAtendimento tipo;
        private String descricao;
        private Boolean ativa;
        private Boolean operacional;
        private List<CozinhaResponse> cozinhas;
        private Long unidadesConsumoAtivas;

        public Builder id(Long v) { this.id = v; return this; }
        public Builder nome(String v) { this.nome = v; return this; }
        public Builder tipo(TipoUnidadeAtendimento v) { this.tipo = v; return this; }
        public Builder descricao(String v) { this.descricao = v; return this; }
        public Builder ativa(Boolean v) { this.ativa = v; return this; }
        public Builder operacional(Boolean v) { this.operacional = v; return this; }
        public Builder cozinhas(List<CozinhaResponse> v) { this.cozinhas = v; return this; }
        public Builder unidadesConsumoAtivas(Long v) { this.unidadesConsumoAtivas = v; return this; }

        public UnidadeAtendimentoResponse build() {
            return new UnidadeAtendimentoResponse(id, nome, tipo, descricao, ativa, operacional, cozinhas, unidadesConsumoAtivas);
        }
    }
}
