package com.restaurante.platform.observabilidade.dto;

import com.restaurante.model.enums.TenantEstado;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TenantObservabilidadeResumoResponse {
    private Long tenantId;
    private String tenantNome;
    private String tenantCode;
    private TenantEstado estado;

    private long totalInstituicoes;
    private long totalUnidades;

    private long turnosAbertos;
    private long devicesAtivos;
    private long devicesOffline;

    private long pagamentosPendentes;
    private long pagamentosCriticos;
    private long pedidosHoje;
    private long subPedidosEmAberto;

    private PlatformAlertLevel alertLevel;
    private PlatformActionRecommended actionRecommended;
    private LocalDateTime ultimaAtividadeEm;
}

