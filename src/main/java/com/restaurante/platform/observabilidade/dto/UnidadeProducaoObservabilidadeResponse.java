package com.restaurante.platform.observabilidade.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UnidadeProducaoObservabilidadeResponse {
    private Long unidadeProducaoId;
    private String nome;
    private long totalFila;
    private long emPreparacao;
    private long prontos;
    private LocalDateTime maisAntigoEmPreparacao;
    private PlatformAlertLevel alertLevel;
}

