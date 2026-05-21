package com.restaurante.financeiro.snapshot.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SnapshotVerificacaoIntegridadeResponse {
    private boolean valido;
    private Boolean hashValido;
    private Boolean assinaturaValida;
    private Boolean assinaturaKeyFound;
    private String assinaturaKeyStatus;
    private String assinaturaFailureReason;

    private String hashPersistido;
    private String hashRecalculado;
    private String assinaturaPersistida;
    private String assinaturaRecalculada;
    private LocalDateTime verificadoEm;
    private String motivo;
}
