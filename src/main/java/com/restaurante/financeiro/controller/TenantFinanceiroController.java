package com.restaurante.financeiro.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.financeiro.monitoramento.dto.CallbackLogResumoDTO;
import com.restaurante.financeiro.monitoramento.dto.FinanceiroTenantResumoDTO;
import com.restaurante.financeiro.monitoramento.dto.PagamentoMonitoramentoFiltro;
import com.restaurante.financeiro.monitoramento.dto.PagamentoResumoDTO;
import com.restaurante.financeiro.service.PagamentoMonitoramentoService;
import com.restaurante.model.enums.CallbackProcessingStatus;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/tenant/financeiro")
@RequiredArgsConstructor
@Tag(name = "Tenant Financeiro", description = "Monitoramento financeiro tenant-scoped (pagamentos e callbacks)")
public class TenantFinanceiroController {

    private final TenantGuard tenantGuard;
    private final PagamentoMonitoramentoService monitoramentoService;

    @GetMapping("/pagamentos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<PagamentoResumoDTO>>> listarPagamentos(
            @RequestParam(required = false) com.restaurante.financeiro.enums.StatusPagamentoGateway statusPagamento,
            @RequestParam(required = false) StatusFinanceiroPedido statusFinanceiroPedido,
            @RequestParam(required = false) String externalReference,
            @RequestParam(required = false) String pedidoNumero,
            @RequestParam(required = false) Integer pendenteHaMaisDeMinutos,
            @RequestParam(required = false) Boolean somenteDivergentes,
            @RequestParam(required = false) Boolean somenteComCallbackInvalido,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime ate,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        TenantContext ctx = tenantGuard.requireContext();
        if (ctx.tenantId() == null) {
            throw new com.restaurante.exception.BusinessException("TenantContext obrigatório para listar pagamentos.");
        }
        PagamentoMonitoramentoFiltro filtro = new PagamentoMonitoramentoFiltro();
        filtro.setStatusPagamento(statusPagamento);
        filtro.setStatusFinanceiroPedido(statusFinanceiroPedido);
        filtro.setExternalReference(externalReference);
        filtro.setPedidoNumero(pedidoNumero);
        filtro.setPendenteHaMaisDeMinutos(pendenteHaMaisDeMinutos);
        filtro.setSomenteDivergentes(somenteDivergentes);
        filtro.setSomenteComCallbackInvalido(somenteComCallbackInvalido);
        filtro.setDe(de);
        filtro.setAte(ate);

        Page<PagamentoResumoDTO> page = monitoramentoService.listarPagamentosDoTenant(ctx.tenantId(), filtro, pageable);
        return ResponseEntity.ok(ApiResponse.success("Pagamentos listados", page));
    }

    @GetMapping("/pagamentos/{pagamentoId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PagamentoResumoDTO>> buscarPagamento(@PathVariable Long pagamentoId) {
        TenantContext ctx = tenantGuard.requireContext();
        if (ctx.tenantId() == null) {
            throw new com.restaurante.exception.BusinessException("TenantContext obrigatório para buscar pagamento.");
        }
        PagamentoResumoDTO dto = monitoramentoService.buscarPagamentoDoTenant(ctx.tenantId(), pagamentoId);
        return ResponseEntity.ok(ApiResponse.success("Pagamento", dto));
    }

    @GetMapping("/callbacks")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<CallbackLogResumoDTO>>> listarCallbacks(
            @RequestParam(required = false) CallbackProcessingStatus processingStatus,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        TenantContext ctx = tenantGuard.requireContext();
        if (ctx.tenantId() == null) {
            throw new com.restaurante.exception.BusinessException("TenantContext obrigatório para listar callbacks.");
        }
        Page<CallbackLogResumoDTO> page = monitoramentoService.listarCallbacksDoTenant(ctx.tenantId(), processingStatus, pageable);
        return ResponseEntity.ok(ApiResponse.success("Callbacks listados", page));
    }

    @GetMapping("/resumo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<FinanceiroTenantResumoDTO>> resumo() {
        TenantContext ctx = tenantGuard.requireContext();
        if (ctx.tenantId() == null) {
            throw new com.restaurante.exception.BusinessException("TenantContext obrigatório para resumo.");
        }
        FinanceiroTenantResumoDTO dto = monitoramentoService.resumoTenant(ctx.tenantId());
        return ResponseEntity.ok(ApiResponse.success("Resumo financeiro", dto));
    }
}

