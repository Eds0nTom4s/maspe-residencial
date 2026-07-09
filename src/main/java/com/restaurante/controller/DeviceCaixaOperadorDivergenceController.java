package com.restaurante.controller;

import com.restaurante.dto.request.JustificarDivergenciaCaixaOperadorRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.CaixaOperadorDivergenceResponse;
import com.restaurante.dto.response.DeviceErrorResponse;
import com.restaurante.exception.DeviceApiException;
import com.restaurante.financeiro.caixa.divergence.repository.CaixaOperadorDivergenceRepository;
import com.restaurante.financeiro.caixa.divergence.service.CaixaOperadorDivergenceService;
import com.restaurante.model.entity.CaixaOperadorDivergence;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.security.device.DevicePrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/device/caixa-operador/divergences")
@RequiredArgsConstructor
@Tag(name = "Device - Caixa Operador Divergences", description = "Justificativa/submissão de divergências de caixa por operador/device (CASH/TPA)")
public class DeviceCaixaOperadorDivergenceController {

    private final CaixaOperadorDivergenceRepository divergenceRepository;
    private final CaixaOperadorDivergenceService divergenceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CaixaOperadorDivergenceResponse>>> listForDevice(
            @RequestParam(name = "caixaId", required = false) Long caixaId
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        requireCapability(device, DeviceCapability.VIEW_OPERATOR_CASH_DIVERGENCE);
        List<CaixaOperadorDivergence> list;
        if (caixaId != null) {
            list = divergenceRepository.findByTenantIdAndCaixaOperadorSessionId(device.tenantId(), caixaId);
            list = list.stream()
                    .filter(d -> d.getDispositivoOperacional() != null && d.getDispositivoOperacional().getId().equals(device.dispositivoId()))
                    .toList();
        } else {
            // MVP: sem listagem ampla por device; evita varrer tenant todo.
            list = List.of();
        }
        return ResponseEntity.ok(ApiResponse.success("Divergências", list.stream().map(this::map).toList()));
    }

    @GetMapping("/{divergenceId}")
    public ResponseEntity<ApiResponse<CaixaOperadorDivergenceResponse>> get(@PathVariable Long divergenceId) {
        DevicePrincipal device = requireDevicePrincipal();
        requireCapability(device, DeviceCapability.VIEW_OPERATOR_CASH_DIVERGENCE);
        CaixaOperadorDivergence d = divergenceRepository.findById(divergenceId).orElse(null);
        if (d == null || d.getTenant() == null || !d.getTenant().getId().equals(device.tenantId())) {
            return ResponseEntity.ok(ApiResponse.success("Divergência", null));
        }
        if (d.getDispositivoOperacional() == null || !d.getDispositivoOperacional().getId().equals(device.dispositivoId())) {
            return ResponseEntity.ok(ApiResponse.success("Divergência", null));
        }
        return ResponseEntity.ok(ApiResponse.success("Divergência", map(d)));
    }

    @PostMapping("/{divergenceId}/justify")
    public ResponseEntity<ApiResponse<CaixaOperadorDivergenceResponse>> justify(@PathVariable Long divergenceId,
                                                                                @Valid @RequestBody JustificarDivergenciaCaixaOperadorRequest request,
                                                                                HttpServletRequest http) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;

        CaixaOperadorDivergence d = divergenceService.justifyByDevice(
                device,
                divergenceId,
                request.getReasonCategory(),
                request.getDescription(),
                ip,
                ua
        );
        return ResponseEntity.ok(ApiResponse.success("Divergência justificada", map(d)));
    }

    @PostMapping("/{divergenceId}/submit")
    public ResponseEntity<ApiResponse<CaixaOperadorDivergenceResponse>> submit(@PathVariable Long divergenceId,
                                                                               HttpServletRequest http) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;

        CaixaOperadorDivergence d = divergenceService.submitByDevice(device, divergenceId, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Divergência submetida", map(d)));
    }

    private CaixaOperadorDivergenceResponse map(CaixaOperadorDivergence d) {
        CaixaOperadorDivergenceResponse r = new CaixaOperadorDivergenceResponse();
        r.setId(d.getId());
        r.setTenantId(d.getTenant() != null ? d.getTenant().getId() : null);
        r.setUnidadeAtendimentoId(d.getUnidadeAtendimento() != null ? d.getUnidadeAtendimento().getId() : null);
        r.setTurnoOperacionalId(d.getTurnoOperacional() != null ? d.getTurnoOperacional().getId() : null);
        r.setCaixaOperadorSessionId(d.getCaixaOperadorSession() != null ? d.getCaixaOperadorSession().getId() : null);
        r.setDeviceId(d.getDispositivoOperacional() != null ? d.getDispositivoOperacional().getId() : null);
        r.setOperadorUserId(d.getOperador() != null ? d.getOperador().getId() : null);
        r.setStatus(d.getStatus());
        r.setType(d.getType());
        r.setSeverity(d.getSeverity());
        r.setPaymentMethod(d.getPaymentMethod());
        r.setExpectedAmount(d.getExpectedAmount());
        r.setDeclaredAmount(d.getDeclaredAmount());
        r.setDifferenceAmount(d.getDifferenceAmount());
        r.setAbsoluteDifferenceAmount(d.getAbsoluteDifferenceAmount());
        r.setReasonCategory(d.getReasonCategory());
        r.setDescription(d.getDescription());
        r.setSubmittedByUserId(d.getSubmittedBy() != null ? d.getSubmittedBy().getId() : null);
        r.setSubmittedAt(d.getSubmittedAt());
        r.setReviewedByUserId(d.getReviewedBy() != null ? d.getReviewedBy().getId() : null);
        r.setReviewedAt(d.getReviewedAt());
        r.setReviewNotes(d.getReviewNotes());
        return r;
    }

    private static void requireCapability(DevicePrincipal device, DeviceCapability cap) {
        if (device.capabilities() == null || !device.capabilities().contains(cap)) {
            throw new DeviceApiException(HttpStatus.FORBIDDEN,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_FORBIDDEN,
                    "Capability requerida: " + cap.name(),
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    null);
        }
    }

    private DevicePrincipal requireDevicePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;
        if (principal instanceof DevicePrincipal dp) {
            return dp;
        }
        throw new IllegalStateException("DevicePrincipal obrigatório.");
    }
}
