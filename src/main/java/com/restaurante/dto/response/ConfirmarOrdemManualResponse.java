package com.restaurante.dto.response;

import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.model.enums.OrdemPagamentoStatus;
import com.restaurante.model.enums.OrdemPagamentoTipo;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ConfirmarOrdemManualResponse {
    private Long ordemPagamentoId;
    private OrdemPagamentoTipo tipo;
    private OrdemPagamentoStatus status;
    private MetodoPagamentoManual metodoConfirmado;
    private BigDecimal valor;
    private BigDecimal valorRecebido;
    private BigDecimal troco;
    private String codigoConsumo;
    private BigDecimal saldoAtual;
    private Long pedidoId;
    private Long turnoOperacionalId;
    private Long confirmadoPorDeviceId;
    private boolean idempotentReplay;
    private LocalDateTime confirmadoEm;
}

