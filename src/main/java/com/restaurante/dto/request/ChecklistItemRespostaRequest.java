package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ChecklistItemRespostaRequest {
    @NotBlank
    private String codigo;
    private Boolean valorBoolean;
    private String valorTexto;
    private BigDecimal valorNumero;
    private String observacao;
}

