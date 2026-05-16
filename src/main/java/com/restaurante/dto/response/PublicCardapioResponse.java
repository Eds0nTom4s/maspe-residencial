package com.restaurante.dto.response;

import java.util.List;

public class PublicCardapioResponse {

    private QrPublicContext qr;
    private List<PublicCategoriaProdutoResponse> categorias;

    public QrPublicContext getQr() {
        return qr;
    }

    public void setQr(QrPublicContext qr) {
        this.qr = qr;
    }

    public List<PublicCategoriaProdutoResponse> getCategorias() {
        return categorias;
    }

    public void setCategorias(List<PublicCategoriaProdutoResponse> categorias) {
        this.categorias = categorias;
    }
}

