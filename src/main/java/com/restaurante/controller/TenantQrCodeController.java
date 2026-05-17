package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.TenantQrCodeResponse;
import com.restaurante.service.tenantadmin.TenantAdminQrService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tenant")
@RequiredArgsConstructor
@Tag(name = "Tenant Admin - QR", description = "Gestão básica de QR codes operacionais do tenant")
public class TenantQrCodeController {

    private final TenantAdminQrService qrService;

    @GetMapping("/qrcodes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<TenantQrCodeResponse>>> listar() {
        return ResponseEntity.ok(ApiResponse.success("QR Codes", qrService.listarQrCodes()));
    }

    @GetMapping("/qrcodes/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantQrCodeResponse>> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("QR Code", qrService.buscarQrCode(id)));
    }

    @PostMapping("/qrcodes/{id}/revogar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantQrCodeResponse>> revogar(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("QR revogado", qrService.revogar(id)));
    }

    @PostMapping("/mesas/{mesaId}/qrcode")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantQrCodeResponse>> gerarQrMesa(@PathVariable Long mesaId) {
        TenantQrCodeResponse qr = qrService.gerarQrParaMesa(mesaId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("QR criado", qr));
    }
}

