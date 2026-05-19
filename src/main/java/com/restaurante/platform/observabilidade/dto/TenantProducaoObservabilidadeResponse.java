package com.restaurante.platform.observabilidade.dto;

import lombok.Data;

import java.util.List;

@Data
public class TenantProducaoObservabilidadeResponse {
    private long totalSubPedidos;
    private long emPreparacao;
    private long prontos;
    private long atrasados;
    private List<UnidadeProducaoObservabilidadeResponse> porUnidadeProducao;
    private List<PlatformAlertaOperacionalResponse> alertas;
}

