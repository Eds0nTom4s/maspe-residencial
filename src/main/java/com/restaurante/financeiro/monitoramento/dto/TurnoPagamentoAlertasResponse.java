package com.restaurante.financeiro.monitoramento.dto;

import com.restaurante.model.enums.TurnoOperacionalStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class TurnoPagamentoAlertasResponse {
    private Long turnoId;
    private TurnoOperacionalStatus statusTurno;
    private long totalPagamentosPendentes;
    private BigDecimal valorPendente;
    private long totalWarnings;
    private long totalCriticos;
    private boolean bloqueiaFecho;
    private List<TurnoPagamentoAlertaItemResponse> avisos;
    private List<PagamentoPendenteResponse> pagamentosCriticos;
    private PagamentoPendenteResumoResponse pagamentosPendentesResumo;
}

