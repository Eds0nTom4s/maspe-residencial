package com.restaurante.financeiro.snapshot.evidence.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EvidenceBundleTurnoDTO {
    private Long turnoId;
    private String status;
    private LocalDateTime abertoEm;
    private LocalDateTime fechadoEm;
    private Long abertoPorUserId;
    private Long fechadoPorUserId;
    private boolean fechamentoForcado;
    private String observacaoFecho;
}

