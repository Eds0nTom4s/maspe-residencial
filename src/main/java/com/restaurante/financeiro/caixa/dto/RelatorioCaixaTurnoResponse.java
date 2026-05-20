package com.restaurante.financeiro.caixa.dto;

import com.restaurante.financeiro.monitoramento.dto.TurnoPagamentoAlertasResponse;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class RelatorioCaixaTurnoResponse {
    private Long turnoId;
    private Long tenantId;
    private Long instituicaoId;
    private Long unidadeAtendimentoId;
    private String statusTurno;
    private LocalDateTime abertoEm;
    private LocalDateTime fechadoEm;
    private LocalDateTime geradoEm;

    private BigDecimal totalGeralConfirmado = BigDecimal.ZERO;
    private BigDecimal totalManualConfirmado = BigDecimal.ZERO;
    private BigDecimal totalGatewayConfirmado = BigDecimal.ZERO;
    private BigDecimal totalPendente = BigDecimal.ZERO;
    private BigDecimal totalFalhado = BigDecimal.ZERO;
    private BigDecimal totalDivergente = BigDecimal.ZERO;

    private Integer quantidadePagamentosConfirmados = 0;
    private Integer quantidadePagamentosPendentes = 0;
    private Integer quantidadeOrdensManuaisConfirmadas = 0;

    private BigDecimal totalPagamentoPedidos = BigDecimal.ZERO;
    private BigDecimal totalCarregamentoFundoConsumo = BigDecimal.ZERO;

    private List<TotalPorMetodoPagamentoResponse> totaisPorMetodo = new ArrayList<>();
    private List<TotalPorOrigemPagamentoResponse> totaisPorOrigem = new ArrayList<>();
    private List<TotalPorDeviceResponse> totaisPorDevice = new ArrayList<>();
    private List<PagamentoResumoCaixaResponse> pagamentosPendentes = new ArrayList<>();
    private List<OrdemPagamentoResumoCaixaResponse> ordensPendentes = new ArrayList<>();
    private TurnoPagamentoAlertasResponse alertasFinanceiros;
    private List<EventoFinanceiroTurnoResponse> eventosRecentes = new ArrayList<>();
}

