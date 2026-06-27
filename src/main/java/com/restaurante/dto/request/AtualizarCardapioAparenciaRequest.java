package com.restaurante.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public class AtualizarCardapioAparenciaRequest {

    @Size(max = 500, message = "URL do banner deve ter no máximo 500 caracteres")
    private String urlBanner;

    @Min(value = 1, message = "Limite de itens por pedido deve ser no mínimo 1")
    @Max(value = 100, message = "Limite de itens por pedido deve ser no máximo 100")
    private Integer maxItensPorPedido;

    public String getUrlBanner() {
        return urlBanner;
    }

    public void setUrlBanner(String urlBanner) {
        this.urlBanner = urlBanner;
    }

    public Integer getMaxItensPorPedido() {
        return maxItensPorPedido;
    }

    public void setMaxItensPorPedido(Integer maxItensPorPedido) {
        this.maxItensPorPedido = maxItensPorPedido;
    }
}
