package com.restaurante.platform.observabilidade.dto;

import lombok.Data;

import java.util.List;

@Data
public class TenantObservabilidadeDetalheResponse {
    private TenantObservabilidadeResumoResponse resumo;
    private List<TurnoObservabilidadeResponse> turnosAbertos;
    private List<DeviceObservabilidadeResponse> devicesProblema;
    private List<PlatformPagamentoObservabilidadeResponse> pagamentosCriticos;
    private TenantProducaoObservabilidadeResponse producao;
    private List<OperationalEventObservabilidadeResponse> eventosRecentes;
    private List<PlatformAlertaOperacionalResponse> alertas;
}

