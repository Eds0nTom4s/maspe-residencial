package com.restaurante.dto.response;

import lombok.Data;

@Data
public class GerirConsumoOptionsResponse {
    private boolean consumoAnonimoDisponivel = true;
    private boolean consumoIdentificadoDisponivel = false; // OTP não implementado nesta fase
    private boolean carregamentoManualDisponivel = true;
    private boolean pagamentoManualPedidoDisponivel = true;
    private String mensagem;
}

