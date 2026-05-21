package com.restaurante.controller;

import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.CancelarTurnoRequest;
import com.restaurante.dto.request.FecharTurnoRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.ChecklistTemplateResponse;
import com.restaurante.dto.response.TurnoOperacionalResponse;
import com.restaurante.dto.response.TurnoPreFechoResponse;
import com.restaurante.financeiro.snapshot.evidence.dto.SnapshotFinanceiroEvidenceBundleResponse;
import com.restaurante.model.enums.ChecklistTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TurnoOperacionalStatus;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.financeiro.snapshot.evidence.service.SnapshotFinanceiroEvidenceBundleService;
import com.restaurante.service.operacao.ChecklistOperacionalService;
import com.restaurante.service.operacao.TurnoOperacionalService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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
import java.util.List;

@RestController
@RequestMapping("/tenant/operacao")
@RequiredArgsConstructor
@Tag(name = "Tenant - Operação (Turnos/Checklists)", description = "Abertura/fecho operacional e disciplina diária por unidade")
public class TenantOperacaoController {

    private final TenantGuard tenantGuard;
    private final TurnoOperacionalService turnoService;
    private final ChecklistOperacionalService checklistService;
    private final SnapshotFinanceiroEvidenceBundleService evidenceBundleService;

    @PostMapping("/turnos/abrir")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TurnoOperacionalResponse>> abrir(@Valid @RequestBody AbrirTurnoRequest request, HttpServletRequest http) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER
        );
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        TurnoOperacionalResponse resp = turnoService.abrirTurno(request, ip, ua);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Turno aberto", resp));
    }

    @GetMapping("/turnos/atual")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TurnoOperacionalResponse>> atual(@RequestParam Long instituicaoId,
                                                                       @RequestParam Long unidadeAtendimentoId) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_FINANCE,
                TenantUserRole.TENANT_CASHIER,
                TenantUserRole.TENANT_KITCHEN
        );
        return ResponseEntity.ok(ApiResponse.success("Turno atual", turnoService.getTurnoAtual(instituicaoId, unidadeAtendimentoId)));
    }

    @GetMapping("/turnos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<TurnoOperacionalResponse>>> listar(@RequestParam(required = false) Long instituicaoId,
                                                                              @RequestParam(required = false) Long unidadeAtendimentoId,
                                                                              @RequestParam(required = false) TurnoOperacionalStatus status,
                                                                              @RequestParam(required = false) LocalDateTime de,
                                                                              @RequestParam(required = false) LocalDateTime ate,
                                                                              Pageable pageable) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_FINANCE,
                TenantUserRole.TENANT_CASHIER,
                TenantUserRole.TENANT_KITCHEN
        );
        return ResponseEntity.ok(ApiResponse.success("Turnos do tenant", turnoService.listar(instituicaoId, unidadeAtendimentoId, status, de, ate, pageable)));
    }

    @GetMapping("/turnos/{turnoId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TurnoOperacionalResponse>> detalhar(@PathVariable Long turnoId) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_FINANCE,
                TenantUserRole.TENANT_CASHIER,
                TenantUserRole.TENANT_KITCHEN
        );
        return ResponseEntity.ok(ApiResponse.success("Detalhe do turno", turnoService.detalhar(turnoId)));
    }

    @PostMapping("/turnos/{turnoId}/iniciar-fecho")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TurnoOperacionalResponse>> iniciarFecho(@PathVariable Long turnoId, HttpServletRequest http) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER
        );
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        return ResponseEntity.ok(ApiResponse.success("Fecho iniciado", turnoService.iniciarFecho(turnoId, ip, ua)));
    }

    @GetMapping("/turnos/{turnoId}/pre-fecho")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TurnoPreFechoResponse>> preFecho(@PathVariable Long turnoId) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_FINANCE,
                TenantUserRole.TENANT_CASHIER
        );
        return ResponseEntity.ok(ApiResponse.success("Pré-fecho", turnoService.preFecho(turnoId)));
    }

    @GetMapping("/turnos/{turnoId}/snapshot/evidence-bundle")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SnapshotFinanceiroEvidenceBundleResponse>> evidenceBundle(@PathVariable Long turnoId,
                                                                                               HttpServletRequest http) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_FINANCE,
                TenantUserRole.TENANT_CASHIER
        );
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        SnapshotFinanceiroEvidenceBundleResponse resp = evidenceBundleService.gerar(turnoId, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Evidence bundle do snapshot financeiro", resp));
    }

    @PostMapping("/turnos/{turnoId}/fechar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TurnoOperacionalResponse>> fechar(@PathVariable Long turnoId,
                                                                        @Valid @RequestBody FecharTurnoRequest request,
                                                                        HttpServletRequest http) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER
        );
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        return ResponseEntity.ok(ApiResponse.success("Turno fechado", turnoService.fechar(turnoId, request, ip, ua)));
    }

    @PostMapping("/turnos/{turnoId}/cancelar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TurnoOperacionalResponse>> cancelar(@PathVariable Long turnoId,
                                                                          @Valid @RequestBody CancelarTurnoRequest request,
                                                                          HttpServletRequest http) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        return ResponseEntity.ok(ApiResponse.success("Turno cancelado", turnoService.cancelar(turnoId, request, ip, ua)));
    }

    @GetMapping("/checklists/templates")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ChecklistTemplateResponse>>> templates(@RequestParam ChecklistTipo tipo) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER
        );
        Long tenantId = tenantGuard.requireContext().tenantId();
        return ResponseEntity.ok(ApiResponse.success("Templates de checklist ativos", checklistService.listarTemplatesAtivos(tenantId, tipo)));
    }
}
