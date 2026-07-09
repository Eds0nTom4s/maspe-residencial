package com.restaurante.controller;

import com.restaurante.dto.request.RevisarCaixaOperadorRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.CaixaOperadorSessionItemResponse;
import com.restaurante.dto.response.CaixaOperadorSessionResponse;
import com.restaurante.financeiro.caixa.evidence.service.CaixaOperadorEvidenceService;
import com.restaurante.financeiro.caixa.repository.CaixaOperadorSessionItemRepository;
import com.restaurante.financeiro.caixa.repository.CaixaOperadorSessionRepository;
import com.restaurante.financeiro.caixa.service.CaixaOperadorSessionService;
import com.restaurante.financeiro.snapshot.evidence.dto.OperatorCashEvidenceSectionDTO;
import com.restaurante.model.entity.CaixaOperadorSession;
import com.restaurante.model.entity.CaixaOperadorSessionItem;
import com.restaurante.model.enums.CaixaOperadorSessionStatus;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tenant/caixa-operador")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Tenant Admin - Caixa Operador", description = "Fecho/revisão de caixa por operador/device (CASH/TPA)")
public class TenantCaixaOperadorController {

    private final TenantGuard tenantGuard;
    private final CaixaOperadorSessionRepository caixaRepository;
    private final CaixaOperadorSessionItemRepository itemRepository;
    private final CaixaOperadorSessionService caixaOperadorSessionService;
    private final CaixaOperadorEvidenceService caixaOperadorEvidenceService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CaixaOperadorSessionResponse>>> list(
            @RequestParam(name = "status", required = false) CaixaOperadorSessionStatus status,
            @RequestParam(name = "unidadeId", required = false) Long unidadeId,
            @RequestParam(name = "turnoId", required = false) Long turnoId,
            @RequestParam(name = "deviceId", required = false) Long deviceId,
            @RequestParam(name = "operadorUserId", required = false) Long operadorUserId,
            Pageable pageable
    ) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();

        Page<CaixaOperadorSession> page = caixaRepository.searchByTenantAndFilters(
                ctx.tenantId(), status, unidadeId, turnoId, deviceId, operadorUserId, pageable
        );
        return ResponseEntity.ok(ApiResponse.success("Caixas", page.map(this::map)));
    }

    @GetMapping("/{caixaId}")
    public ResponseEntity<ApiResponse<CaixaOperadorSessionResponse>> detail(@PathVariable Long caixaId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();

        CaixaOperadorSession caixa = caixaRepository.findById(caixaId).orElse(null);
        if (caixa == null || !caixa.getTenant().getId().equals(ctx.tenantId())) {
            return ResponseEntity.ok(ApiResponse.success("Caixa", null));
        }
        return ResponseEntity.ok(ApiResponse.success("Caixa", map(caixa)));
    }

    @GetMapping("/{caixaId}/items")
    public ResponseEntity<ApiResponse<List<CaixaOperadorSessionItemResponse>>> items(@PathVariable Long caixaId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();

        List<CaixaOperadorSessionItem> items = itemRepository.findByTenantIdAndCaixaOperadorSessionId(ctx.tenantId(), caixaId);
        List<CaixaOperadorSessionItemResponse> mapped = items.stream().map(this::mapItem).toList();
        return ResponseEntity.ok(ApiResponse.success("Items", mapped));
    }

    @PostMapping("/{caixaId}/review")
    public ResponseEntity<ApiResponse<CaixaOperadorSessionResponse>> review(@PathVariable Long caixaId,
                                                                            @Valid @RequestBody RevisarCaixaOperadorRequest request) {
        CaixaOperadorSession caixa = caixaOperadorSessionService.revisar(caixaId, request.getStatus(), request.getReviewNotes());
        return ResponseEntity.ok(ApiResponse.success("Revisão registrada", map(caixa)));
    }

    @GetMapping("/evidence-preview")
    public ResponseEntity<ApiResponse<OperatorCashEvidenceSectionDTO>> evidencePreview(
            @RequestParam(name = "turnoId") Long turnoId
    ) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = tenantGuard.requireContext();

        // O snapshot/evidence bundle atual é por turno; preview é apenas leitura.
        // periodStart/periodEnd são informativos (aberto/fechado do turno) e podem ser null se o turno não estiver fechado.
        OperatorCashEvidenceSectionDTO section = caixaOperadorEvidenceService.buildForTurno(
                ctx.tenantId(),
                null,
                null,
                turnoId,
                null,
                null
        );
        return ResponseEntity.ok(ApiResponse.success("Evidence preview", section));
    }

    private CaixaOperadorSessionResponse map(CaixaOperadorSession caixa) {
        CaixaOperadorSessionResponse r = new CaixaOperadorSessionResponse();
        r.setId(caixa.getId());
        r.setStatus(caixa.getStatus());
        r.setTenantId(caixa.getTenant() != null ? caixa.getTenant().getId() : null);
        r.setInstituicaoId(caixa.getInstituicao() != null ? caixa.getInstituicao().getId() : null);
        r.setUnidadeAtendimentoId(caixa.getUnidadeAtendimento() != null ? caixa.getUnidadeAtendimento().getId() : null);
        r.setTurnoOperacionalId(caixa.getTurnoOperacional() != null ? caixa.getTurnoOperacional().getId() : null);
        r.setDeviceId(caixa.getDispositivoOperacional() != null ? caixa.getDispositivoOperacional().getId() : null);
        r.setOperadorUserId(caixa.getOperador() != null ? caixa.getOperador().getId() : null);
        r.setOpenedAt(caixa.getOpenedAt());
        r.setClosedAt(caixa.getClosedAt());
        r.setReviewedAt(caixa.getReviewedAt());
        r.setExpectedCashAmount(caixa.getExpectedCashAmount());
        r.setDeclaredCashAmount(caixa.getDeclaredCashAmount());
        r.setCashDifferenceAmount(caixa.getCashDifferenceAmount());
        r.setExpectedTpaAmount(caixa.getExpectedTpaAmount());
        r.setDeclaredTpaAmount(caixa.getDeclaredTpaAmount());
        r.setTpaDifferenceAmount(caixa.getTpaDifferenceAmount());
        r.setExpectedManualTotalAmount(caixa.getExpectedManualTotalAmount());
        r.setDeclaredManualTotalAmount(caixa.getDeclaredManualTotalAmount());
        r.setManualDifferenceAmount(caixa.getManualDifferenceAmount());
        r.setCurrency(caixa.getCurrency());
        return r;
    }

    private CaixaOperadorSessionItemResponse mapItem(CaixaOperadorSessionItem item) {
        CaixaOperadorSessionItemResponse r = new CaixaOperadorSessionItemResponse();
        r.setId(item.getId());
        r.setOrdemPagamentoId(item.getOrdemPagamento() != null ? item.getOrdemPagamento().getId() : null);
        r.setPagamentoId(item.getPagamento() != null ? item.getPagamento().getId() : null);
        r.setPedidoId(item.getPedido() != null ? item.getPedido().getId() : null);
        r.setSessaoConsumoId(item.getSessaoConsumo() != null ? item.getSessaoConsumo().getId() : null);
        r.setPaymentMethod(item.getPaymentMethod());
        r.setAmount(item.getAmount());
        r.setConfirmedAt(item.getConfirmedAt());
        r.setSource(item.getSource());
        return r;
    }
}
