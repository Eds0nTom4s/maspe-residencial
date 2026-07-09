package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.DeviceSessaoConsumoPorTelefoneResponse;
import com.restaurante.exception.DeviceUnauthorizedException;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.consumo.identificacao.service.TelefoneNormalizerService;
import com.restaurante.service.device.DeviceConsumoIdentificadoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/device/sessoes-consumo")
@RequiredArgsConstructor
@Tag(name = "Device - Sessão Consumo Identificada", description = "Lookup operacional de SessaoConsumo por telefone (após OTP)")
public class DeviceSessaoConsumoIdentificadoController {

    private final DeviceConsumoIdentificadoService deviceConsumoIdentificadoService;
    private final TelefoneNormalizerService phoneNormalizerService;

    @GetMapping("/por-telefone")
    public ResponseEntity<ApiResponse<DeviceSessaoConsumoPorTelefoneResponse>> porTelefone(
            @RequestParam("telefone") String telefone,
            HttpServletRequest http
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        var list = deviceConsumoIdentificadoService.listarSessoesAbertasPorTelefone(device, telefone, ip, ua);
        DeviceSessaoConsumoPorTelefoneResponse resp = new DeviceSessaoConsumoPorTelefoneResponse();
        resp.setTelefoneMascarado(phoneNormalizerService.mask(phoneNormalizerService.normalizeOrThrow(telefone)));
        resp.setSessoesAtivas(list);
        return ResponseEntity.ok(ApiResponse.success("Sessões ativas", resp));
    }

    private DevicePrincipal requireDevicePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof DevicePrincipal device)) {
            throw new DeviceUnauthorizedException("DevicePrincipal ausente.");
        }
        return device;
    }
}
