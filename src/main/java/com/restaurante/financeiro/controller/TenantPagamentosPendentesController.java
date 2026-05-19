package com.restaurante.financeiro.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.financeiro.enums.MetodoPagamentoAppyPay;
import com.restaurante.financeiro.enums.PagamentoPollingStatus;
import com.restaurante.financeiro.monitoramento.dto.ManualPollRequest;
import com.restaurante.financeiro.monitoramento.dto.PagamentoManualPollResponse;
import com.restaurante.financeiro.monitoramento.dto.PagamentoPendenteResponse;
import com.restaurante.financeiro.monitoramento.dto.PagamentoPendenteResumoResponse;
import com.restaurante.financeiro.monitoramento.dto.PagamentoPollingDetalheResponse;
import com.restaurante.financeiro.monitoramento.dto.TurnoPagamentoAlertasResponse;
import com.restaurante.financeiro.service.PagamentoManualPollingService;
import com.restaurante.financeiro.service.PagamentoPendenteQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/tenant/financeiro")
@RequiredArgsConstructor
@Tag(name = "Tenant Financeiro - Pendências", description = "Visibilidade operacional de pagamentos pendentes + polling manual + alertas por turno")
public class TenantPagamentosPendentesController {

    private final PagamentoPendenteQueryService queryService;
    private final PagamentoManualPollingService manualPollingService;

    @GetMapping("/pagamentos/pendentes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<PagamentoPendenteResponse>>> listarPendentes(
            @RequestParam(required = false) Long turnoId,
            @RequestParam(required = false) Long pedidoId,
            @RequestParam(required = false) Long unidadeAtendimentoId,
            @RequestParam(required = false) MetodoPagamentoAppyPay metodoPagamento,
            @RequestParam(required = false) PagamentoPollingStatus pollingStatus,
            @RequestParam(required = false) Boolean hasError,
            @RequestParam(required = false) Integer olderThanMinutes,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime ate,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        Page<PagamentoPendenteResponse> page = queryService.listarPendentes(
                turnoId,
                pedidoId,
                unidadeAtendimentoId,
                metodoPagamento,
                pollingStatus,
                hasError,
                olderThanMinutes,
                de,
                ate,
                pageable
        );
        return ResponseEntity.ok(ApiResponse.success("Pagamentos pendentes", page));
    }

    @GetMapping("/pagamentos/pendentes/resumo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PagamentoPendenteResumoResponse>> resumoPendentes(
            @RequestParam(required = false) Long turnoId,
            @RequestParam(required = false) Long unidadeAtendimentoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime ate
    ) {
        PagamentoPendenteResumoResponse resp = queryService.resumoPendentes(turnoId, unidadeAtendimentoId, de, ate);
        return ResponseEntity.ok(ApiResponse.success("Resumo pendências", resp));
    }

    @GetMapping("/pagamentos/{pagamentoId}/polling")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PagamentoPollingDetalheResponse>> detalhePolling(@PathVariable Long pagamentoId) {
        PagamentoPollingDetalheResponse resp = queryService.detalhePolling(pagamentoId);
        return ResponseEntity.ok(ApiResponse.success("Detalhe polling", resp));
    }

    @PostMapping("/pagamentos/{pagamentoId}/poll")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PagamentoManualPollResponse>> pollManual(
            @PathVariable Long pagamentoId,
            @RequestBody(required = false) ManualPollRequest request
    ) {
        PagamentoManualPollResponse resp = manualPollingService.forcarPolling(pagamentoId, request);
        return ResponseEntity.ok(ApiResponse.success("Polling manual executado", resp));
    }

    @GetMapping("/turnos/{turnoId}/alertas-pagamento")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TurnoPagamentoAlertasResponse>> alertasTurno(@PathVariable Long turnoId) {
        TurnoPagamentoAlertasResponse resp = queryService.alertasPorTurno(turnoId);
        return ResponseEntity.ok(ApiResponse.success("Alertas de pagamento do turno", resp));
    }
}

