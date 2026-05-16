package com.restaurante.financeiro.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.financeiro.monitoramento.dto.CallbackLogDetalheDTO;
import com.restaurante.financeiro.monitoramento.dto.CallbackLogResumoDTO;
import com.restaurante.financeiro.monitoramento.dto.FinanceiroPlatformResumoDTO;
import com.restaurante.financeiro.monitoramento.dto.PagamentoMonitoramentoFiltro;
import com.restaurante.financeiro.monitoramento.dto.PagamentoResumoDTO;
import com.restaurante.financeiro.service.PagamentoMonitoramentoService;
import com.restaurante.model.enums.CallbackProcessingStatus;
import com.restaurante.model.enums.StatusFinanceiroPedido;
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
@RequestMapping("/platform/financeiro")
@RequiredArgsConstructor
@Tag(name = "Platform Financeiro", description = "Monitoramento financeiro global (PLATFORM_ADMIN)")
public class PlatformFinanceiroController {

    private final TenantGuard tenantGuard;
    private final PagamentoMonitoramentoService monitoramentoService;

    @GetMapping("/pagamentos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<PagamentoResumoDTO>>> listarPagamentos(
            @RequestParam(required = false) Long tenantId,
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
        tenantGuard.assertPlatformAdmin();
        PagamentoMonitoramentoFiltro filtro = new PagamentoMonitoramentoFiltro();
        filtro.setTenantId(tenantId);
        filtro.setStatusPagamento(statusPagamento);
        filtro.setStatusFinanceiroPedido(statusFinanceiroPedido);
        filtro.setExternalReference(externalReference);
        filtro.setPedidoNumero(pedidoNumero);
        filtro.setPendenteHaMaisDeMinutos(pendenteHaMaisDeMinutos);
        filtro.setSomenteDivergentes(somenteDivergentes);
        filtro.setSomenteComCallbackInvalido(somenteComCallbackInvalido);
        filtro.setDe(de);
        filtro.setAte(ate);

        Page<PagamentoResumoDTO> page = monitoramentoService.listarPagamentosPlatform(filtro, pageable);
        return ResponseEntity.ok(ApiResponse.success("Pagamentos listados", page));
    }

    @GetMapping("/callbacks")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<CallbackLogResumoDTO>>> listarCallbacks(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) CallbackProcessingStatus processingStatus,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        tenantGuard.assertPlatformAdmin();
        Page<CallbackLogResumoDTO> page = monitoramentoService.listarCallbacksPlatform(tenantId, processingStatus, pageable);
        return ResponseEntity.ok(ApiResponse.success("Callbacks listados", page));
    }

    @GetMapping("/callbacks/sem-tenant")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<CallbackLogResumoDTO>>> listarCallbacksSemTenant(
            @PageableDefault(size = 50) Pageable pageable
    ) {
        tenantGuard.assertPlatformAdmin();
        Page<CallbackLogResumoDTO> page = monitoramentoService.listarCallbacksSemTenant(pageable);
        return ResponseEntity.ok(ApiResponse.success("Callbacks sem tenant", page));
    }

    @GetMapping("/callbacks/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CallbackLogDetalheDTO>> detalheCallback(@PathVariable Long id) {
        tenantGuard.assertPlatformAdmin();
        CallbackLogDetalheDTO dto = monitoramentoService.buscarCallbackLogDetalhePlatform(id);
        return ResponseEntity.ok(ApiResponse.success("Callback log", dto));
    }

    @GetMapping("/resumo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<FinanceiroPlatformResumoDTO>> resumo() {
        tenantGuard.assertPlatformAdmin();
        FinanceiroPlatformResumoDTO dto = monitoramentoService.resumoPlatform();
        return ResponseEntity.ok(ApiResponse.success("Resumo financeiro platform", dto));
    }
}

