package com.restaurante.controller;

import com.restaurante.dto.request.DeviceCriarPedidoRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.DevicePedidoResponse;
import com.restaurante.service.device.DevicePedidoService;
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
@RequestMapping("/device/pedidos")
@RequiredArgsConstructor
@Tag(name = "Device - Pedidos", description = "Criação online de pedidos por POS (deviceToken) com idempotência")
public class DevicePedidoController {

    private final DevicePedidoService devicePedidoService;

    @PostMapping
    public ResponseEntity<ApiResponse<DevicePedidoResponse>> criar(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody DeviceCriarPedidoRequest request,
            HttpServletRequest http
    ) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        DevicePedidoResponse resp = devicePedidoService.criarPedido(request, idempotencyKey, ua, ip);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Pedido criado", resp));
    }

    @GetMapping("/{pedidoId}")
    public ResponseEntity<ApiResponse<DevicePedidoResponse>> buscar(@PathVariable Long pedidoId) {
        DevicePedidoResponse resp = devicePedidoService.buscarPedido(pedidoId);
        return ResponseEntity.ok(ApiResponse.success("Pedido", resp));
    }
}

