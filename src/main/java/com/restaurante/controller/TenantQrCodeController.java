package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.TenantQrCodeResponse;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.tenantadmin.TenantAdminQrService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tenant")
@RequiredArgsConstructor
@Tag(name = "Tenant Admin - QR", description = "Gestão básica de QR codes operacionais do tenant")
public class TenantQrCodeController {

    private final TenantGuard tenantGuard;
    private final TenantAdminQrService qrService;

    @GetMapping("/qrcodes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<TenantQrCodeResponse>>> listar() {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER
        );
        return ResponseEntity.ok(ApiResponse.success("QR Codes", qrService.listarQrCodes()));
    }

    @GetMapping("/qrcodes/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantQrCodeResponse>> buscar(@PathVariable Long id) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER
        );
        return ResponseEntity.ok(ApiResponse.success("QR Code", qrService.buscarQrCode(id)));
    }

    @PostMapping("/qrcodes/{id}/revogar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantQrCodeResponse>> revogar(@PathVariable Long id) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        return ResponseEntity.ok(ApiResponse.success("QR revogado", qrService.revogar(id)));
    }

    @PatchMapping("/qrcodes/{id}/revogar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantQrCodeResponse>> revogarPatch(@PathVariable Long id) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        return ResponseEntity.ok(ApiResponse.success("QR revogado", qrService.revogar(id)));
    }

    @PostMapping("/qrcodes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantQrCodeResponse>> gerarGenerico() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("QR criado", qrService.gerarQrGenerico()));
    }

    @PostMapping("/qrcodes/principal")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantQrCodeResponse>> gerarPrincipal() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("QR principal criado", qrService.gerarQrPrincipal()));
    }

    @PostMapping("/qrcodes/mesa/{mesaId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantQrCodeResponse>> gerarQrMesaAlias(@PathVariable Long mesaId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantQrCodeResponse qr = qrService.gerarQrParaMesa(mesaId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("QR criado", qr));
    }

    @PostMapping("/qrcodes/{id}/regenerar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantQrCodeResponse>> regenerar(@PathVariable Long id) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("QR regenerado", qrService.regenerar(id)));
    }

    @GetMapping("/qrcodes/{id}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @RequestParam(defaultValue = "PNG") String formato) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER
        );
        var download = qrService.baixar(id, formato);
        return ResponseEntity.ok()
                .contentType(download.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"")
                .body(download.bytes());
    }

    @PostMapping("/mesas/{mesaId}/qrcode")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantQrCodeResponse>> gerarQrMesa(@PathVariable Long mesaId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantQrCodeResponse qr = qrService.gerarQrParaMesa(mesaId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("QR criado", qr));
    }

    @GetMapping("/qr/principal")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<com.restaurante.dto.response.TenantQrPrincipalResponse>> principal() {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER
        );
        var resp = qrService.buscarQrPrincipal();
        return ResponseEntity.ok(ApiResponse.success("QR principal encontrado.", resp));
    }
}
