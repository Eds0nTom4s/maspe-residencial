package com.restaurante.dto.request;

import jakarta.validation.constraints.Size;

public class DespublicarCardapioRequest {

    @Size(max = 500)
    private String motivo;

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
}
