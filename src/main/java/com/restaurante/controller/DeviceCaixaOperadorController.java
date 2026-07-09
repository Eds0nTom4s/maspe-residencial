package com.restaurante.controller;

import com.restaurante.dto.request.AbrirCaixaOperadorRequest;
import com.restaurante.dto.request.FecharCaixaOperadorRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.CaixaOperadorSessionResponse;
import com.restaurante.financeiro.caixa.service.CaixaOperadorSessionService;
import com.restaurante.model.entity.CaixaOperadorSession;
import com.restaurante.security.device.DevicePrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/device/caixa-operador")
@RequiredArgsConstructor
@Tag(name = "Device - Caixa Operador", description = "Abertura/fecho de caixa por operador/device (CASH/TPA)")
public class DeviceCaixaOperadorController {

    private final CaixaOperadorSessionService caixaOperadorSessionService;

    @PostMapping("/open")
    public ResponseEntity<ApiResponse<CaixaOperadorSessionResponse>> open(@Valid @RequestBody AbrirCaixaOperadorRequest request,
                                                                          HttpServletRequest http) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;

        CaixaOperadorSession caixa = caixaOperadorSessionService.abrir(
                device,
                request.getOperadorUserId(),
                request.getTurnoId(),
                request.getNotes(),
                ip,
                ua
        );
        return ResponseEntity.ok(ApiResponse.success("Caixa aberto", map(caixa)));
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<CaixaOperadorSessionResponse>> current() {
        DevicePrincipal device = requireDevicePrincipal();
        CaixaOperadorSession caixa = caixaOperadorSessionService.buscarOpenDoDevice(device);
        return ResponseEntity.ok(ApiResponse.success("Caixa atual", caixa != null ? map(caixa) : null));
    }

    @GetMapping("/{caixaId}")
    public ResponseEntity<ApiResponse<CaixaOperadorSessionResponse>> get(@PathVariable Long caixaId) {
        DevicePrincipal device = requireDevicePrincipal();
        CaixaOperadorSession caixa = caixaOperadorSessionService.buscarPorIdDoDevice(device, caixaId);
        return ResponseEntity.ok(ApiResponse.success("Caixa", caixa != null ? map(caixa) : null));
    }

    @PostMapping("/{caixaId}/close")
    public ResponseEntity<ApiResponse<CaixaOperadorSessionResponse>> close(@PathVariable Long caixaId,
                                                                           @Valid @RequestBody FecharCaixaOperadorRequest request,
                                                                           HttpServletRequest http) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;

        CaixaOperadorSession caixa = caixaOperadorSessionService.fechar(
                device,
                caixaId,
                request.getClosedByUserId(),
                request.getDeclaredCashAmount(),
                request.getDeclaredTpaAmount(),
                request.getCloseReason(),
                request.getNotes(),
                ip,
                ua
        );
        return ResponseEntity.ok(ApiResponse.success("Caixa fechado", map(caixa)));
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

    private DevicePrincipal requireDevicePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;
        if (principal instanceof DevicePrincipal dp) {
            return dp;
        }
        throw new IllegalStateException("DevicePrincipal obrigatório.");
    }
}
