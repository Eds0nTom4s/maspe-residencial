package com.restaurante.dto.response;

import com.restaurante.model.enums.TipoCozinha;

public class CozinhaResponse {

    private Long id;
    private String nome;
    private TipoCozinha tipo;
    private String impressoraId;
    private Boolean ativa;
    private Long subPedidosAtivos;

    public CozinhaResponse() {}

    public CozinhaResponse(Long id, String nome, TipoCozinha tipo, String impressoraId, Boolean ativa, Long subPedidosAtivos) {
        this.id = id;
        this.nome = nome;
        this.tipo = tipo;
        this.impressoraId = impressoraId;
        this.ativa = ativa;
        this.subPedidosAtivos = subPedidosAtivos;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public TipoCozinha getTipo() { return tipo; }
    public void setTipo(TipoCozinha tipo) { this.tipo = tipo; }
    public String getImpressoraId() { return impressoraId; }
    public void setImpressoraId(String impressoraId) { this.impressoraId = impressoraId; }
    public Boolean getAtiva() { return ativa; }
    public void setAtiva(Boolean ativa) { this.ativa = ativa; }
    public Long getSubPedidosAtivos() { return subPedidosAtivos; }
    public void setSubPedidosAtivos(Long subPedidosAtivos) { this.subPedidosAtivos = subPedidosAtivos; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long id;
        private String nome;
        private TipoCozinha tipo;
        private String impressoraId;
        private Boolean ativa;
        private Long subPedidosAtivos;

        public Builder id(Long v) { this.id = v; return this; }
        public Builder nome(String v) { this.nome = v; return this; }
        public Builder tipo(TipoCozinha v) { this.tipo = v; return this; }
        public Builder impressoraId(String v) { this.impressoraId = v; return this; }
        public Builder ativa(Boolean v) { this.ativa = v; return this; }
        public Builder subPedidosAtivos(Long v) { this.subPedidosAtivos = v; return this; }

        public CozinhaResponse build() {
            return new CozinhaResponse(id, nome, tipo, impressoraId, ativa, subPedidosAtivos);
        }
    }
}
