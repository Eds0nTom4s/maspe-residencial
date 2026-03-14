package com.restaurante.dto.request;

import com.restaurante.model.enums.TipoUnidadeAtendimento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CriarUnidadeAtendimentoRequest {

    @NotBlank(message = "Nome é obrigatório")
    private String nome;

    @NotNull(message = "Tipo é obrigatório")
    private TipoUnidadeAtendimento tipo;

    private String descricao;

    public CriarUnidadeAtendimentoRequest() {}

    public CriarUnidadeAtendimentoRequest(String nome, TipoUnidadeAtendimento tipo, String descricao) {
        this.nome = nome;
        this.tipo = tipo;
        this.descricao = descricao;
    }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public TipoUnidadeAtendimento getTipo() { return tipo; }
    public void setTipo(TipoUnidadeAtendimento tipo) { this.tipo = tipo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String nome;
        private TipoUnidadeAtendimento tipo;
        private String descricao;

        public Builder nome(String v) { this.nome = v; return this; }
        public Builder tipo(TipoUnidadeAtendimento v) { this.tipo = v; return this; }
        public Builder descricao(String v) { this.descricao = v; return this; }

        public CriarUnidadeAtendimentoRequest build() {
            return new CriarUnidadeAtendimentoRequest(nome, tipo, descricao);
        }
    }
}
