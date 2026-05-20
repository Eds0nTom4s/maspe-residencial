package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.ReimpressaoDocumentoResponse;
import com.restaurante.service.device.DeviceConsumoDocumentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/device/consumos")
@RequiredArgsConstructor
@Tag(name = "Device - Consumos", description = "Reimpressão lógica de QR/conta/comprovativos para operação presencial")
public class DeviceConsumoController {

    private final DeviceConsumoDocumentService deviceConsumoDocumentService;

    @PostMapping("/{codigoConsumo}/reimprimir-qr")
    public ResponseEntity<ApiResponse<ReimpressaoDocumentoResponse>> reimprimirQr(@PathVariable String codigoConsumo, HttpServletRequest http) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        return ResponseEntity.ok(ApiResponse.success("Documento", deviceConsumoDocumentService.reimprimirQrConsumo(codigoConsumo, ip, ua)));
    }

    @PostMapping("/{codigoConsumo}/reimprimir-conta")
    public ResponseEntity<ApiResponse<ReimpressaoDocumentoResponse>> reimprimirConta(@PathVariable String codigoConsumo, HttpServletRequest http) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        return ResponseEntity.ok(ApiResponse.success("Documento", deviceConsumoDocumentService.reimprimirConta(codigoConsumo, ip, ua)));
    }

    @PostMapping("/ordens-pagamento/{ordemId}/reimprimir-comprovativo")
    public ResponseEntity<ApiResponse<ReimpressaoDocumentoResponse>> reimprimirComprovativo(@PathVariable Long ordemId, HttpServletRequest http) {
        String ua = http != null ? http.getHeader("User-Agent") : null;
        String ip = http != null ? http.getRemoteAddr() : null;
        return ResponseEntity.ok(ApiResponse.success("Documento", deviceConsumoDocumentService.reimprimirComprovativoOrdem(ordemId, ip, ua)));
    }
}

