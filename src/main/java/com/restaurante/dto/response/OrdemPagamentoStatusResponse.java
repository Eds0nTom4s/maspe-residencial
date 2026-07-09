package com.restaurante.dto.response;

import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.model.enums.OrdemPagamentoStatus;
import com.restaurante.model.enums.OrdemPagamentoTipo;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrdemPagamentoStatusResponse {
    private OrdemPagamentoStatus status;
    private OrdemPagamentoTipo tipo;
    private Long ordemPagamentoId;
    private BigDecimal valor;
    private String moeda;
    private MetodoPagamentoManual metodoSolicitado;
    private Long pedidoId;
    private String codigoConsumo;
    private BigDecimal saldoAtual;
    private boolean podeBaixarQr;
    private String mensagem;
}

