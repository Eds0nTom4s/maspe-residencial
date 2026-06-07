package com.restaurante.dto.response;

import java.util.List;

public class PublicCardapioResponse {

    private QrPublicContext qr;
    private Boolean publicado;
    private String mensagem;
    private String telefoneContato;
    private List<PublicCategoriaProdutoResponse> categorias;

    public QrPublicContext getQr() {
        return qr;
    }

    public void setQr(QrPublicContext qr) {
        this.qr = qr;
    }

    public Boolean getPublicado() {
        return publicado;
    }

    public void setPublicado(Boolean publicado) {
        this.publicado = publicado;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    public String getTelefoneContato() {
        return telefoneContato;
    }

    public void setTelefoneContato(String telefoneContato) {
        this.telefoneContato = telefoneContato;
    }

    public List<PublicCategoriaProdutoResponse> getCategorias() {
        return categorias;
    }

    public void setCategorias(List<PublicCategoriaProdutoResponse> categorias) {
        this.categorias = categorias;
    }
}
