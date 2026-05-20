package com.restaurante.controller;

import com.restaurante.dto.request.ConfirmarOrdemManualRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.ConfirmarOrdemManualResponse;
import com.restaurante.dto.response.DeviceOrdemPagamentoResponse;
import com.restaurante.service.device.DeviceOrdemPagamentoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/device/ordens-pagamento")
@RequiredArgsConstructor
@Tag(name = "Device - Ordens de Pagamento", description = "Scan e confirmação manual (CASH/TPA) por POS autorizado")
public class DeviceOrdemPagamentoController {

    private final DeviceOrdemPagamentoService deviceOrdemPagamentoService;

    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<DeviceOrdemPagamentoResponse>> escanear(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success("Ordem", deviceOrdemPagamentoService.escanearPorToken(token)));
    }

    @PostMapping("/{ordemId}/confirmar-manual")
    public ResponseEntity<ApiResponse<ConfirmarOrdemManualResponse>> confirmar(
            @PathVariable Long ordemId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ConfirmarOrdemManualRequest request,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        ConfirmarOrdemManualResponse resp = deviceOrdemPagamentoService.confirmarManual(ordemId, request, idempotencyKey, ua, ip);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("Ordem confirmada", resp));
    }
}

