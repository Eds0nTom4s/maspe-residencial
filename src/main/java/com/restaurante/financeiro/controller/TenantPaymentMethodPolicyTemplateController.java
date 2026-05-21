package com.restaurante.financeiro.controller;

import com.restaurante.dto.request.CreatePaymentPolicyTemplateRequest;
import com.restaurante.dto.request.PaymentPolicyRolloutRequest;
import com.restaurante.dto.request.UpdatePaymentPolicyTemplateRequest;
import com.restaurante.dto.response.*;
import com.restaurante.financeiro.paymentmethod.entity.PaymentMethodPolicyTemplate;
import com.restaurante.financeiro.paymentmethod.entity.PaymentMethodPolicyRollout;
import com.restaurante.financeiro.paymentmethod.entity.PaymentMethodPolicyTemplateItem;
import com.restaurante.financeiro.paymentmethod.service.PaymentMethodPolicyAsyncRolloutService;
import com.restaurante.financeiro.paymentmethod.service.PaymentMethodPolicyRolloutService;
import com.restaurante.financeiro.paymentmethod.service.PaymentMethodPolicyTemplateService;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tenant/payment-policy-templates")
@RequiredArgsConstructor
@Tag(name = "Payment Policy Templates", description = "Templates de políticas de métodos de pagamento (tenant-aware) + rollout por unidade/device-type")
public class TenantPaymentMethodPolicyTemplateController {

    private final TenantGuard tenantGuard;
    private final PaymentMethodPolicyTemplateService templateService;
    private final PaymentMethodPolicyRolloutService rolloutService;
    private final PaymentMethodPolicyAsyncRolloutService asyncRolloutService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<PaymentPolicyTemplateResponse>>> list() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE, TenantUserRole.TENANT_CASHIER);
        List<PaymentPolicyTemplateResponse> resp = templateService.listTemplates().stream().map(this::toResponseNoItems).toList();
        return ResponseEntity.ok(ApiResponse.success("Templates", resp));
    }

    @GetMapping("/{templateId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentPolicyTemplateResponse>> get(@PathVariable Long templateId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE, TenantUserRole.TENANT_CASHIER);
        PaymentMethodPolicyTemplate t = templateService.getTemplateWithItems(templateId);
        return ResponseEntity.ok(ApiResponse.success("Template", toResponse(t)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentPolicyTemplateResponse>> create(@Valid @RequestBody CreatePaymentPolicyTemplateRequest req, HttpServletRequest http) {
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        PaymentMethodPolicyTemplate saved = templateService.create(req, ip, ua);
        PaymentMethodPolicyTemplate loaded = templateService.getTemplateWithItems(saved.getId());
        return ResponseEntity.ok(ApiResponse.success("Template criado", toResponse(loaded)));
    }

    @PutMapping("/{templateId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentPolicyTemplateResponse>> update(@PathVariable Long templateId, @Valid @RequestBody UpdatePaymentPolicyTemplateRequest req, HttpServletRequest http) {
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        PaymentMethodPolicyTemplate saved = templateService.update(templateId, req, ip, ua);
        PaymentMethodPolicyTemplate loaded = templateService.getTemplateWithItems(saved.getId());
        return ResponseEntity.ok(ApiResponse.success("Template atualizado", toResponse(loaded)));
    }

    @PostMapping("/{templateId}/activate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentPolicyTemplateResponse>> activate(@PathVariable Long templateId, HttpServletRequest http) {
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        PaymentMethodPolicyTemplate saved = templateService.activate(templateId, ip, ua);
        PaymentMethodPolicyTemplate loaded = templateService.getTemplateWithItems(saved.getId());
        return ResponseEntity.ok(ApiResponse.success("Template ativado", toResponse(loaded)));
    }

    @PostMapping("/{templateId}/deactivate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentPolicyTemplateResponse>> deactivate(@PathVariable Long templateId, HttpServletRequest http) {
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        PaymentMethodPolicyTemplate saved = templateService.deactivate(templateId, ip, ua);
        PaymentMethodPolicyTemplate loaded = templateService.getTemplateWithItems(saved.getId());
        return ResponseEntity.ok(ApiResponse.success("Template desativado", toResponse(loaded)));
    }

    @PostMapping("/{templateId}/rollout/preview")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentPolicyRolloutPreviewResponse>> preview(@PathVariable Long templateId, @Valid @RequestBody PaymentPolicyRolloutRequest req, HttpServletRequest http) {
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        PaymentPolicyRolloutPreviewResponse resp = rolloutService.preview(templateId, req, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Preview de rollout", resp));
    }

    @PostMapping("/{templateId}/rollout/apply")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentPolicyRolloutApplyResponse>> apply(@PathVariable Long templateId, @Valid @RequestBody PaymentPolicyRolloutRequest req, HttpServletRequest http) {
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        PaymentPolicyRolloutApplyResponse resp = rolloutService.apply(templateId, req, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Rollout aplicado", resp));
    }

    @PostMapping("/{templateId}/rollout/submit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentPolicyRolloutSubmitResponse>> submitAsync(
            @PathVariable Long templateId,
            @Valid @RequestBody PaymentPolicyRolloutRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            HttpServletRequest http
    ) {
        String ip = http != null ? http.getRemoteAddr() : null;
        String ua = http != null ? http.getHeader("User-Agent") : null;
        try {
            PaymentMethodPolicyRollout r = asyncRolloutService.submit(templateId, req, idempotencyKeyHeader, ip, ua);
            return ResponseEntity.ok(ApiResponse.success("Rollout submetido", toSubmitResponse(r)));
        } catch (PaymentMethodPolicyAsyncRolloutService.ExistingRolloutException e) {
            return ResponseEntity.ok(ApiResponse.success("Rollout já submetido (idempotente)", toSubmitResponse(e.getRollout())));
        }
    }

    private PaymentPolicyTemplateResponse toResponseNoItems(PaymentMethodPolicyTemplate t) {
        PaymentPolicyTemplateResponse r = new PaymentPolicyTemplateResponse();
        r.setTemplateId(t.getId());
        r.setCode(t.getCode());
        r.setName(t.getName());
        r.setDescription(t.getDescription());
        r.setTargetDeviceType(t.getTargetDeviceType());
        r.setStatus(t.getStatus());
        r.setSystemDefault(t.isSystemDefault());
        r.setVersion(t.getVersion());
        r.setCreatedAt(t.getCreatedAt());
        r.setUpdatedAt(t.getUpdatedAt());
        r.setItems(null);
        return r;
    }

    private PaymentPolicyTemplateResponse toResponse(PaymentMethodPolicyTemplate t) {
        PaymentPolicyTemplateResponse r = toResponseNoItems(t);
        List<PaymentPolicyTemplateItemResponse> items = t.getItems() != null
                ? t.getItems().stream().map(this::toItemResponse).toList()
                : List.of();
        r.setItems(items);
        return r;
    }

    private PaymentPolicyTemplateItemResponse toItemResponse(PaymentMethodPolicyTemplateItem i) {
        PaymentPolicyTemplateItemResponse r = new PaymentPolicyTemplateItemResponse();
        r.setPaymentMethodCode(i.getPaymentMethodCode());
        r.setPolicyStatus(i.getPolicyStatus());
        r.setEnabledForPos(i.getEnabledForPos());
        r.setEnabledForPedido(i.getEnabledForPedido());
        r.setEnabledForFundoConsumo(i.getEnabledForFundoConsumo());
        r.setCanConfirmManual(i.getCanConfirmManual());
        r.setCanStartGateway(i.getCanStartGateway());
        r.setMinAmount(i.getMinAmount());
        r.setMaxAmount(i.getMaxAmount());
        r.setOverrideReason(i.getOverrideReason());
        r.setMetadata(templateService.readMetadata(i.getMetadataJson()));
        return r;
    }

    private PaymentPolicyRolloutSubmitResponse toSubmitResponse(PaymentMethodPolicyRollout r) {
        PaymentPolicyRolloutSubmitResponse s = new PaymentPolicyRolloutSubmitResponse();
        s.setRolloutId(r.getId());
        s.setStatus(r.getStatus());
        s.setTotalItems(r.getTotalItems());
        s.setTotalDevicesTargeted(r.getTotalDevicesTargeted());
        s.setRequestedAt(r.getRequestedAt());
        return s;
    }
}
