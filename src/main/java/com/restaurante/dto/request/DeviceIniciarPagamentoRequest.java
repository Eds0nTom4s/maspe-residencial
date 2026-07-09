package com.restaurante.dto.request;

import com.restaurante.financeiro.enums.MetodoPagamentoAppyPay;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeviceIniciarPagamentoRequest {
    @NotBlank
    private String clientRequestId;
    @NotNull
    private MetodoPagamentoAppyPay metodoPagamento;
    private String telefoneCliente;
    private String descricao;
    private String returnUrl;
}

