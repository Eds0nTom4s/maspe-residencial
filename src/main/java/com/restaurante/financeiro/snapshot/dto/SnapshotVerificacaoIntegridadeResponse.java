package com.restaurante.financeiro.snapshot.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SnapshotVerificacaoIntegridadeResponse {
    private boolean valido;
    private String hashPersistido;
    private String hashRecalculado;
    private LocalDateTime verificadoEm;
    private String motivo;
}

