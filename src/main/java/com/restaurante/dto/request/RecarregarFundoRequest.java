package com.restaurante.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO para recarga de Fundo de Consumo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecarregarFundoRequest {

    @NotNull(message = "Valor é obrigatório")
    @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
    private BigDecimal valor;

    /** Motivo / descrição da recarga (opcional) */
    private String observacoes;
}
