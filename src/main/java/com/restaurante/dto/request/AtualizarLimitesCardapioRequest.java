package com.restaurante.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AtualizarLimitesCardapioRequest {

    @NotNull
    @Min(1)
    private Integer maxCategorias;

    @NotNull
    @Min(1)
    private Integer maxProdutos;

    @Size(max = 500)
    private String motivo;

    @Size(max = 120)
    private String configuradoPor;

    public Integer getMaxCategorias() {
        return maxCategorias;
    }

    public void setMaxCategorias(Integer maxCategorias) {
        this.maxCategorias = maxCategorias;
    }

    public Integer getMaxProdutos() {
        return maxProdutos;
    }

    public void setMaxProdutos(Integer maxProdutos) {
        this.maxProdutos = maxProdutos;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public String getConfiguradoPor() {
        return configuradoPor;
    }

    public void setConfiguradoPor(String configuradoPor) {
        this.configuradoPor = configuradoPor;
    }
}
