package com.restaurante.dto.response;

import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.model.enums.OrdemPagamentoStatus;
import com.restaurante.model.enums.OrdemPagamentoTipo;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DeviceOrdemPagamentoResponse {
    private Long ordemPagamentoId;
    private OrdemPagamentoTipo tipo;
    private OrdemPagamentoStatus status;
    private BigDecimal valor;
    private String moeda;
    private MetodoPagamentoManual metodoSolicitado;
    private String codigoConsumo;
    private Long pedidoId;
    private boolean podeConfirmar;
}

