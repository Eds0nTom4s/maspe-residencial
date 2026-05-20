package com.restaurante.financeiro.caixa.dto;

import com.restaurante.financeiro.monitoramento.dto.TurnoPagamentoAlertasResponse;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Snapshot financeiro congelado no momento do fecho do turno.
 * Não deve ser recalculado retroativamente depois do turno FECHADO.
 *
 * Conteúdo sanitizado (sem payload bruto de gateway, tokens, secrets, hashes).
 */
@Data
public class ResumoFinanceiroFechoTurnoSnapshot {
    private String snapshotVersion;
    private LocalDateTime geradoEm;

    private Long turnoId;
    private Long tenantId;
    private Long instituicaoId;
    private Long unidadeAtendimentoId;
    private String statusTurnoNoMomento;
    private LocalDateTime abertoEm;
    private LocalDateTime fechadoEm;

    private BigDecimal totalGeralConfirmado;
    private BigDecimal totalManualConfirmado;
    private BigDecimal totalGatewayConfirmado;

    private BigDecimal totalCash;
    private BigDecimal totalTpa;
    private BigDecimal totalAppyPay;

    private BigDecimal totalPendente;
    private BigDecimal totalFalhado;
    private BigDecimal totalDivergente;

    private long totalPagamentosPendentes;
    private long totalCriticos;

    private BigDecimal totalCarregamentoFundo;
    private BigDecimal totalPagamentoPedidos;

    private Integer quantidadePagamentosConfirmados;
    private Integer quantidadePagamentosPendentes;
    private Integer quantidadeOrdensManuaisConfirmadas;

    private List<TotalPorMetodoPagamentoResponse> totaisPorMetodo;
    private List<TotalPorOrigemPagamentoResponse> totaisPorOrigem;
    private TurnoPagamentoAlertasResponse alertasFinanceiros;

    private com.restaurante.financeiro.snapshot.dto.SnapshotIntegridadeResponse integridade;

    private Map<String, Object> observacoes;
}
