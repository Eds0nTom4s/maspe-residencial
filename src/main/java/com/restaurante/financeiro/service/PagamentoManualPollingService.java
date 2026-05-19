package com.restaurante.financeiro.service;

import com.restaurante.financeiro.monitoramento.dto.ManualPollRequest;
import com.restaurante.financeiro.monitoramento.dto.PagamentoManualPollResponse;
import com.restaurante.financeiro.polling.PagamentoGatewayPollingService;
import com.restaurante.financeiro.polling.PagamentoPollingResult;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PagamentoManualPollingService {

    private final TenantGuard tenantGuard;
    private final PagamentoGatewayRepository pagamentoGatewayRepository;
    private final PagamentoGatewayPollingService pollingService;
    private final OperationalEventLogService operationalEventLogService;

    public PagamentoManualPollResponse forcarPolling(Long pagamentoId, ManualPollRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();

        Pagamento pagamento = pagamentoGatewayRepository.findByIdAndTenantId(pagamentoId, ctx.tenantId())
                .orElseThrow(() -> new com.restaurante.exception.ResourceNotFoundException("Recurso não encontrado."));

        OperationalOrigem origem = origemFromContext(ctx);
        operationalEventLogService.logPagamentoEvent(
                OperationalEventType.PAGAMENTO_POLLING_MANUAL_SOLICITADO,
                pagamento,
                origem,
                "Polling manual solicitado",
                Map.of("motivo", sanitize(request != null ? request.getMotivo() : null)),
                null,
                null
        );

        PagamentoPollingResult result = pollingService.pollPagamentoForManual(pagamentoId);

        Pagamento refreshed = pagamentoGatewayRepository.findById(pagamentoId).orElse(pagamento);
        operationalEventLogService.logPagamentoEvent(
                OperationalEventType.PAGAMENTO_POLLING_MANUAL_EXECUTADO,
                refreshed,
                origem,
                "Polling manual executado",
                Map.of(
                        "gatewayStatus", result.getGatewayStatus() != null ? result.getGatewayStatus().name() : "UNKNOWN",
                        "confirmado", result.isConfirmado(),
                        "attempts", result.getAttempts()
                ),
                null,
                null
        );

        PagamentoManualPollResponse resp = new PagamentoManualPollResponse();
        resp.setPagamentoId(pagamentoId);
        resp.setPedidoId(refreshed.getPedido() != null ? refreshed.getPedido().getId() : null);
        resp.setStatusAnterior(result.getStatusAnterior());
        resp.setStatusAtual(result.getStatusAtual());
        resp.setPollingStatus(result.getPollingStatus());
        resp.setGatewayStatus(result.getGatewayStatus());
        resp.setConfirmado(result.isConfirmado());
        resp.setValor(refreshed.getAmount());
        resp.setMoeda("AOA");
        resp.setAttempts(result.getAttempts());
        resp.setMessage(result.getMessage());
        resp.setExecutadoEm(LocalDateTime.now());
        return resp;
    }

    private static String sanitize(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() > 500) t = t.substring(0, 500);
        return t;
    }

    private OperationalOrigem origemFromContext(TenantContext ctx) {
        if (ctx == null || ctx.roles() == null) return OperationalOrigem.TENANT_ADMIN;
        if (ctx.roles().contains(TenantUserRole.TENANT_OWNER.name())) return OperationalOrigem.TENANT_OWNER;
        if (ctx.roles().contains(TenantUserRole.TENANT_ADMIN.name())) return OperationalOrigem.TENANT_ADMIN;
        if (ctx.roles().contains(TenantUserRole.TENANT_FINANCE.name())) return OperationalOrigem.TENANT_FINANCE;
        return OperationalOrigem.TENANT_ADMIN;
    }
}
