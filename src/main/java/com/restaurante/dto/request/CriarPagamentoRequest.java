package com.restaurante.dto.request;

import com.restaurante.model.enums.MetodoPagamento;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO para criação de pagamento
 * Estrutura preparada para integração com gateways
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CriarPagamentoRequest {

    @NotNull(message = "ID da unidade de consumo é obrigatório")
    private Long unidadeConsumoId;

    @NotNull(message = "Valor é obrigatório")
    private BigDecimal valor;

    @NotNull(message = "Método de pagamento é obrigatório")
    private MetodoPagamento metodoPagamento;

    private String observacoes;
}
