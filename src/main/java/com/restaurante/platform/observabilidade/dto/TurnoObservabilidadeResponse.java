package com.restaurante.platform.observabilidade.dto;

import com.restaurante.model.enums.TurnoOperacionalStatus;
import com.restaurante.model.enums.TurnoOperacionalTipo;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TurnoObservabilidadeResponse {
    private Long turnoId;
    private TurnoOperacionalStatus status;
    private TurnoOperacionalTipo tipo;
    private Long instituicaoId;
    private Long unidadeAtendimentoId;
    private LocalDateTime abertoEm;
    private LocalDateTime fechadoEm;

    private Long pedidosTotal;
    private Long pagamentosPendentes;
    private Long pagamentosCriticos;
    private Long subPedidosEmAberto;
    private Long devicesOffline;
    private Boolean podeFechar;
    private PlatformAlertLevel alertLevel;
}

