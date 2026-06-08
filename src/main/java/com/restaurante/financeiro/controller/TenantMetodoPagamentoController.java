package com.restaurante.financeiro.controller;

import com.restaurante.dto.request.TenantMetodoPagamentoUpdateRequest;
import com.restaurante.dto.request.UpdateTenantPaymentMethodRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.TenantMetodoPagamentoResponse;
import com.restaurante.financeiro.paymentmethod.entity.TenantPaymentMethod;
import com.restaurante.financeiro.paymentmethod.service.TenantPaymentMethodAdminService;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodStatus;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tenant/metodos-pagamento")
@RequiredArgsConstructor
@Tag(name = "Tenant Métodos de Pagamento", description = "Configuração operacional de métodos de pagamento do tenant")
public class TenantMetodoPagamentoController {

    private final TenantGuard tenantGuard;
    private final TenantPaymentMethodAdminService adminService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<TenantMetodoPagamentoResponse>>> listar() {
        assertCanRead();
        return ResponseEntity.ok(ApiResponse.success("Métodos de pagamento", adminService.listar().stream().map(this::toResponse).toList()));
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<TenantMetodoPagamentoResponse>>> atualizarTodos(
            @Valid @RequestBody List<TenantMetodoPagamentoUpdateRequest> request,
            HttpServletRequest http) {
        assertCanWrite();
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        List<TenantMetodoPagamentoResponse> updated = request.stream()
                .map(item -> adminService.atualizar(item.getCodigo(), toUpdateRequest(item), ip, ua))
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Métodos de pagamento atualizados", updated));
    }

    @PatchMapping("/{codigo}/ativar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantMetodoPagamentoResponse>> ativar(@PathVariable PaymentMethodCode codigo, HttpServletRequest http) {
        assertCanWrite();
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        return ResponseEntity.ok(ApiResponse.success("Método ativado", toResponse(adminService.activate(codigo, ip, ua))));
    }

    @PatchMapping("/{codigo}/desativar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantMetodoPagamentoResponse>> desativar(@PathVariable PaymentMethodCode codigo, HttpServletRequest http) {
        assertCanWrite();
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        return ResponseEntity.ok(ApiResponse.success("Método desativado", toResponse(adminService.deactivate(codigo, ip, ua))));
    }

    private UpdateTenantPaymentMethodRequest toUpdateRequest(TenantMetodoPagamentoUpdateRequest item) {
        UpdateTenantPaymentMethodRequest req = new UpdateTenantPaymentMethodRequest();
        req.setDisplayName(item.getNome());
        req.setDescription(item.getInstrucoesPublicas());
        req.setStatus(item.getStatus());
        req.setEnabledForQr(item.getDisponivelPublico());
        req.setEnabledForPedido(item.getDisponivelPedido() != null ? item.getDisponivelPedido() : item.getDisponivelPublico());
        req.setEnabledForPos(item.getDisponivelPOS());
        req.setEnabledForFundoConsumo(item.getDisponivelFundoConsumo());
        req.setMinAmount(item.getValorMinimo());
        req.setMaxAmount(item.getValorMaximo());
        req.setSortOrder(item.getOrdem());
        req.setIconKey(item.getIcone());
        req.setMetadata(item.getMetadata());
        return req;
    }

    private TenantMetodoPagamentoResponse toResponse(TenantPaymentMethod m) {
        return TenantMetodoPagamentoResponse.builder()
                .codigo(m.getCode())
                .nome(m.getDisplayName())
                .ativo(m.getStatus() == PaymentMethodStatus.ACTIVE)
                .disponivelPublico(m.isEnabledForQr() || m.isEnabledForPedido())
                .disponivelPOS(m.isEnabledForPos())
                .exigeConfirmacaoManual(m.isRequiresManualConfirmation())
                .gateway(m.getProvider())
                .instrucoesPublicas(m.getDescription())
                .ordem(m.getSortOrder())
                .configuradoEm(m.getUpdatedAt() != null ? m.getUpdatedAt() : m.getCreatedAt())
                .configuradoPor(null)
                .build();
    }

    private void assertCanRead() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE, TenantUserRole.TENANT_CASHIER);
    }

    private void assertCanWrite() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
    }
}
