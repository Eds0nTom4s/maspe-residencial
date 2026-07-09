package com.restaurante.controller;

import com.restaurante.dto.request.DeviceIdentificacaoOtpRequest;
import com.restaurante.dto.request.DeviceIdentificacaoOtpVerifyRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.DeviceIdentificacaoOtpResponse;
import com.restaurante.dto.response.DeviceIdentificacaoOtpVerifyResponse;
import com.restaurante.exception.DeviceUnauthorizedException;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.service.device.DeviceConsumoIdentificadoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/device/sessoes-consumo/{sessaoId}/identificacao")
@RequiredArgsConstructor
@Tag(name = "Device - OTP Assistido (POS)", description = "Fluxo assistido: POS solicita/valida OTP para vincular uma SessaoConsumo específica ao telefone do cliente presente")
public class DeviceConsumoIdentificadoAssistidoController {

    private final DeviceConsumoIdentificadoService deviceConsumoIdentificadoService;

    @PostMapping("/otp/request")
    public ResponseEntity<ApiResponse<DeviceIdentificacaoOtpResponse>> requestOtp(
            @PathVariable Long sessaoId,
            @Valid @RequestBody DeviceIdentificacaoOtpRequest request,
            HttpServletRequest http
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;

        var result = deviceConsumoIdentificadoService.requestOtpVinculacaoAssistida(device, sessaoId, request.getTelefone(), ip, ua);
        DeviceIdentificacaoOtpResponse resp = new DeviceIdentificacaoOtpResponse();
        resp.setChallengeId(result.getChallenge().getId());
        resp.setSessaoConsumoId(sessaoId);
        resp.setTelefoneMascarado(result.getMaskedPhone());
        resp.setExpiresAt(result.getChallenge().getExpiresAt());
        resp.setResendAvailableAt(result.getResendAvailableAt());
        resp.setDebugOtp(result.getDebugOtp());
        return ResponseEntity.ok(ApiResponse.success("OTP solicitado", resp));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<ApiResponse<DeviceIdentificacaoOtpVerifyResponse>> verifyOtp(
            @PathVariable Long sessaoId,
            @Valid @RequestBody DeviceIdentificacaoOtpVerifyRequest request,
            HttpServletRequest http
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;

        var result = deviceConsumoIdentificadoService.verifyOtpVinculacaoAssistida(
                device,
                sessaoId,
                request.getChallengeId(),
                request.getTelefone(),
                request.getOtp(),
                ip,
                ua
        );

        DeviceIdentificacaoOtpVerifyResponse resp = new DeviceIdentificacaoOtpVerifyResponse();
        resp.setClienteConsumoId(result.clienteConsumoId());
        resp.setSessaoConsumoId(result.sessaoConsumoId());
        resp.setTelefoneMascarado(result.telefoneMascarado());
        resp.setIdentificacaoStatus(result.identificacaoStatus());
        resp.setIdentificadoPorOtp(result.identificadoPorOtp());
        resp.setIdentificadoEm(result.identificadoEm());
        resp.setStatusMensagem(result.statusMensagem());
        return ResponseEntity.ok(ApiResponse.success("Sessão identificada", resp));
    }

    private DevicePrincipal requireDevicePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof DevicePrincipal device)) {
            throw new DeviceUnauthorizedException("DevicePrincipal ausente.");
        }
        return device;
    }
}

