package com.restaurante.dto.request;

import com.restaurante.model.enums.MetodoPagamentoManual;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ConfirmarPagamentoManualPedidoRequest {

    @NotNull(message = "Método de pagamento é obrigatório")
    private MetodoPagamentoManual metodoPagamento;

    @NotNull(message = "Valor é obrigatório")
    @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
    private BigDecimal valor;

    @Size(max = 200)
    private String referenciaComprovativo;

    @Size(max = 500)
    private String observacao;
}
