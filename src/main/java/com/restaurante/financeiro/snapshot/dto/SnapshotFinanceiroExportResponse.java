package com.restaurante.financeiro.snapshot.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class SnapshotFinanceiroExportResponse {
    private String exportVersion;
    private LocalDateTime exportadoEm;

    private Long turnoId;
    private Long tenantId;
    private Long instituicaoId;
    private Long unidadeAtendimentoId;
    private String statusTurno;
    private LocalDateTime fechadoEm;

    private String snapshotVersion;
    private JsonNode snapshotFinanceiro;

    private SnapshotIntegridadeResponse integridade;
    private SnapshotVerificacaoIntegridadeResponse verificacao;

    private Map<String, Object> observacoes;
}

