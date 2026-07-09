package com.restaurante.dto.response;

import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class DevicePedidoResponse {
    private Long pedidoId;
    private String numeroPedido;
    private StatusPedido statusOperacional;
    private StatusFinanceiroPedido statusFinanceiro;
    private Long tenantId;
    private Long instituicaoId;
    private Long unidadeAtendimentoId;
    private Long turnoOperacionalId;
    private Long mesaId;
    private BigDecimal total;
    private List<DevicePedidoItemResponse> itens;
    private List<DeviceSubPedidoResponse> subPedidos;
    private LocalDateTime criadoEm;
    private boolean idempotentReplay;
}

