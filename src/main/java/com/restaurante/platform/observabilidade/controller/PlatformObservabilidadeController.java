package com.restaurante.platform.observabilidade.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.model.enums.OperationalActorType;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TurnoOperacionalStatus;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.financeiro.enums.PagamentoPollingStatus;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.platform.observabilidade.dto.PlatformAlertLevel;
import com.restaurante.platform.observabilidade.dto.PlatformAlertaOperacionalResponse;
import com.restaurante.platform.observabilidade.dto.PlatformSaudeOperacionalResponse;
import com.restaurante.platform.observabilidade.dto.TenantObservabilidadeDetalheResponse;
import com.restaurante.platform.observabilidade.dto.TenantObservabilidadeResumoResponse;
import com.restaurante.platform.observabilidade.dto.TurnoObservabilidadeResponse;
import com.restaurante.platform.observabilidade.dto.DeviceObservabilidadeResponse;
import com.restaurante.platform.observabilidade.dto.PlatformPagamentoObservabilidadeResponse;
import com.restaurante.platform.observabilidade.dto.OperationalEventObservabilidadeResponse;
import com.restaurante.platform.observabilidade.dto.TenantProducaoObservabilidadeResponse;
import com.restaurante.platform.observabilidade.service.PlatformObservabilidadeService;
import com.restaurante.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/platform/observabilidade")
@RequiredArgsConstructor
@Tag(name = "Platform Observabilidade", description = "Observabilidade operacional do piloto (PLATFORM_ADMIN)")
public class PlatformObservabilidadeController {

    private final TenantGuard tenantGuard;
    private final PlatformObservabilidadeService service;

    @GetMapping("/saude")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PlatformSaudeOperacionalResponse>> saude() {
        tenantGuard.assertPlatformAdmin();
        return ResponseEntity.ok(ApiResponse.success("Saúde operacional", service.saudeGlobal()));
    }

    @GetMapping("/tenants")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<TenantObservabilidadeResumoResponse>>> tenants(
            @RequestParam(required = false) TenantEstado estado,
            @RequestParam(required = false) Boolean comTurnoAberto,
            @RequestParam(required = false) Boolean comAlertasCriticos,
            @RequestParam(required = false) Boolean comDevicesOffline,
            @RequestParam(required = false) Boolean comPagamentosCriticos,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        tenantGuard.assertPlatformAdmin();
        Page<TenantObservabilidadeResumoResponse> page = service.listarTenants(estado, comTurnoAberto, comAlertasCriticos, comDevicesOffline, comPagamentosCriticos, search, pageable);
        return ResponseEntity.ok(ApiResponse.success("Tenants", page));
    }

    @GetMapping("/tenants/{tenantId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantObservabilidadeDetalheResponse>> detalheTenant(@PathVariable Long tenantId) {
        tenantGuard.assertPlatformAdmin();
        return ResponseEntity.ok(ApiResponse.success("Tenant", service.detalheTenant(tenantId)));
    }

    @GetMapping("/tenants/{tenantId}/turnos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<TurnoObservabilidadeResponse>>> turnos(
            @PathVariable Long tenantId,
            @RequestParam(required = false) TurnoOperacionalStatus status,
            @RequestParam(required = false) Long instituicaoId,
            @RequestParam(required = false) Long unidadeAtendimentoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime ate,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        tenantGuard.assertPlatformAdmin();
        Page<TurnoObservabilidadeResponse> page = service.turnosTenant(tenantId, status, instituicaoId, unidadeAtendimentoId, de, ate, pageable);
        return ResponseEntity.ok(ApiResponse.success("Turnos", page));
    }

    @GetMapping("/tenants/{tenantId}/devices")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<DeviceObservabilidadeResponse>>> devices(
            @PathVariable Long tenantId,
            @RequestParam(required = false) DispositivoStatus status,
            @RequestParam(required = false) DispositivoTipo tipo,
            @RequestParam(required = false) Boolean offline,
            @RequestParam(required = false) Long unidadeAtendimentoId,
            @RequestParam(required = false) Long unidadeProducaoId,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        tenantGuard.assertPlatformAdmin();
        Page<DeviceObservabilidadeResponse> page = service.devicesTenant(tenantId, status, tipo, offline, unidadeAtendimentoId, unidadeProducaoId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Devices", page));
    }

    @GetMapping("/tenants/{tenantId}/pagamentos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<PlatformPagamentoObservabilidadeResponse>>> pagamentos(
            @PathVariable Long tenantId,
            @RequestParam(required = false) StatusPagamentoGateway status,
            @RequestParam(required = false) PagamentoPollingStatus pollingStatus,
            @RequestParam(required = false) Long turnoId,
            @RequestParam(required = false) Long unidadeAtendimentoId,
            @RequestParam(required = false) Boolean criticalOnly,
            @RequestParam(required = false) Integer olderThanMinutes,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        tenantGuard.assertPlatformAdmin();
        Page<PlatformPagamentoObservabilidadeResponse> page = service.pagamentosTenant(tenantId, status, pollingStatus, turnoId, unidadeAtendimentoId, criticalOnly, olderThanMinutes, pageable);
        return ResponseEntity.ok(ApiResponse.success("Pagamentos", page));
    }

    @GetMapping("/tenants/{tenantId}/producao")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantProducaoObservabilidadeResponse>> producao(
            @PathVariable Long tenantId,
            @RequestParam(required = false) Long unidadeProducaoId
    ) {
        tenantGuard.assertPlatformAdmin();
        return ResponseEntity.ok(ApiResponse.success("Produção", service.producaoTenant(tenantId, unidadeProducaoId, null, null, null)));
    }

    @GetMapping("/tenants/{tenantId}/eventos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<OperationalEventObservabilidadeResponse>>> eventos(
            @PathVariable Long tenantId,
            @RequestParam(required = false) OperationalEventType eventType,
            @RequestParam(required = false) OperationalEntityType entityType,
            @RequestParam(required = false) OperationalActorType actorType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime ate,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        tenantGuard.assertPlatformAdmin();
        Page<OperationalEventObservabilidadeResponse> page = service.eventosTenant(tenantId, eventType, entityType, actorType, de, ate, pageable);
        return ResponseEntity.ok(ApiResponse.success("Eventos", page));
    }

    @GetMapping("/alertas")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<PlatformAlertaOperacionalResponse>>> alertas(
            @RequestParam(required = false) PlatformAlertLevel level,
            @RequestParam(required = false) Long tenantId,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        tenantGuard.assertPlatformAdmin();
        Page<PlatformAlertaOperacionalResponse> page = service.alertasAtivos(level, tenantId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Alertas", page));
    }
}

