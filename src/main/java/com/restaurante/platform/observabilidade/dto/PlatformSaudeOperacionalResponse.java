package com.restaurante.platform.observabilidade.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PlatformSaudeOperacionalResponse {
    private long totalTenants;
    private long tenantsAtivos;
    private long turnosAbertos;
    private long turnosEmFecho;
    private long devicesAtivos;
    private long devicesOffline;
    private long pagamentosPendentes;
    private long pagamentosCriticos;
    private long pagamentosMaxAttempts;
    private long pedidosCriadosHoje;
    private long pedidosPendentes;
    private long subPedidosEmPreparacao;
    private long subPedidosProntos;
    private long alertasCriticos;
    private long alertasWarnings;
    private LocalDateTime ultimaAtualizacao;
}

