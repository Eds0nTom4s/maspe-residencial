package com.restaurante.delivery.controller;

import com.restaurante.delivery.dto.request.UpdateProductDeliveryPolicyRequest;
import com.restaurante.delivery.dto.request.UpdateTenantDeliveryPolicyRequest;
import com.restaurante.delivery.dto.response.DeliveryJobResponse;
import com.restaurante.delivery.dto.response.OrderFulfillmentResponse;
import com.restaurante.delivery.dto.response.ProductDeliveryPolicyResponse;
import com.restaurante.delivery.dto.response.TenantDeliveryPolicyResponse;
import com.restaurante.delivery.repository.DeliveryJobRepository;
import com.restaurante.delivery.repository.OrderFulfillmentRepository;
import com.restaurante.delivery.service.DeliveryJobService;
import com.restaurante.delivery.service.OrderFulfillmentService;
import com.restaurante.delivery.service.ProductDeliveryPolicyService;
import com.restaurante.delivery.service.TenantDeliveryPolicyService;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.DeliveryJob;
import com.restaurante.model.entity.OrderFulfillment;
import com.restaurante.model.entity.ProductDeliveryPolicy;
import com.restaurante.model.entity.TenantDeliveryPolicy;
import com.restaurante.model.enums.DeliveryFeePaymentStatus;
import com.restaurante.model.enums.DeliveryJobStatus;
import com.restaurante.model.enums.OrderFulfillmentStatus;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tenant/delivery")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Tenant Admin - Delivery & Fulfillment", description = "Endpoints para gerenciamento de politicas de entrega e fulfillment")
public class TenantDeliveryController {

    private final TenantGuard tenantGuard;
    private final TenantDeliveryPolicyService tenantDeliveryPolicyService;
    private final ProductDeliveryPolicyService productDeliveryPolicyService;
    private final OrderFulfillmentService orderFulfillmentService;
    private final DeliveryJobService deliveryJobService;
    private final OrderFulfillmentRepository orderFulfillmentRepository;
    private final DeliveryJobRepository deliveryJobRepository;

    @GetMapping("/policy")
    public ResponseEntity<ApiResponse<TenantDeliveryPolicyResponse>> getPolicy() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantContext ctx = tenantGuard.requireContext();
        TenantDeliveryPolicyResponse resp = tenantDeliveryPolicyService.getResponse(ctx.tenantId());
        return ResponseEntity.ok(ApiResponse.success("Politica obtida com sucesso", resp));
    }

    @PutMapping("/policy")
    public ResponseEntity<ApiResponse<TenantDeliveryPolicyResponse>> updatePolicy(@Valid @RequestBody UpdateTenantDeliveryPolicyRequest request) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantContext ctx = tenantGuard.requireContext();
        TenantDeliveryPolicyResponse resp = tenantDeliveryPolicyService.update(ctx.tenantId(), request);
        return ResponseEntity.ok(ApiResponse.success("Politica atualizada com sucesso", resp));
    }

    @GetMapping("/product-policies")
    public ResponseEntity<ApiResponse<List<ProductDeliveryPolicyResponse>>> listProductPolicies() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantContext ctx = tenantGuard.requireContext();
        List<ProductDeliveryPolicyResponse> list = productDeliveryPolicyService.list(ctx.tenantId());
        return ResponseEntity.ok(ApiResponse.success("Politicas de produtos listadas", list));
    }

    @PutMapping("/product-policies/{productId}")
    public ResponseEntity<ApiResponse<ProductDeliveryPolicyResponse>> updateProductPolicy(
            @PathVariable Long productId,
            @Valid @RequestBody UpdateProductDeliveryPolicyRequest request
    ) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantContext ctx = tenantGuard.requireContext();
        ProductDeliveryPolicyResponse resp = productDeliveryPolicyService.upsert(ctx.tenantId(), productId, request);
        return ResponseEntity.ok(ApiResponse.success("Politica de produto salva", resp));
    }

    @GetMapping("/fulfillments")
    public ResponseEntity<ApiResponse<List<OrderFulfillmentResponse>>> listFulfillments(
            @RequestParam(name = "status", required = false) OrderFulfillmentStatus status
    ) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantContext ctx = tenantGuard.requireContext();
        List<OrderFulfillment> list;
        if (status != null) {
            list = orderFulfillmentRepository.findByTenantIdAndStatusOrderByIdDesc(ctx.tenantId(), status);
        } else {
            list = orderFulfillmentRepository.findAll().stream()
                    .filter(f -> f.getTenant() != null && ctx.tenantId().equals(f.getTenant().getId()))
                    .toList();
        }
        List<OrderFulfillmentResponse> resp = list.stream().map(this::mapFulfillment).toList();
        return ResponseEntity.ok(ApiResponse.success("Fulfillments listados", resp));
    }

    @GetMapping("/fulfillments/{fulfillmentId}")
    public ResponseEntity<ApiResponse<OrderFulfillmentResponse>> getFulfillment(@PathVariable Long fulfillmentId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantContext ctx = tenantGuard.requireContext();
        OrderFulfillment f = orderFulfillmentRepository.findByTenantIdAndId(ctx.tenantId(), fulfillmentId)
                .orElseThrow(() -> new BusinessException("FULFILLMENT_NOT_FOUND"));
        return ResponseEntity.ok(ApiResponse.success("Fulfillment encontrado", mapFulfillment(f)));
    }

    @PostMapping("/fulfillments/{fulfillmentId}/mark-ready-for-pickup")
    public ResponseEntity<ApiResponse<OrderFulfillmentResponse>> markReadyForPickup(@PathVariable Long fulfillmentId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantContext ctx = tenantGuard.requireContext();
        
        // Find if there is an associated DeliveryJob
        OrderFulfillment f = orderFulfillmentRepository.findByTenantIdAndId(ctx.tenantId(), fulfillmentId)
                .orElseThrow(() -> new BusinessException("FULFILLMENT_NOT_FOUND"));
        
        var jobOpt = deliveryJobRepository.findByTenantIdAndPedido_Id(ctx.tenantId(), f.getPedido().getId());
        if (jobOpt.isPresent()) {
            deliveryJobService.markReadyForPickup(ctx.tenantId(), jobOpt.get().getId());
        } else {
            orderFulfillmentService.markReadyForPickup(ctx.tenantId(), fulfillmentId);
        }
        
        OrderFulfillment updated = orderFulfillmentRepository.findByTenantIdAndId(ctx.tenantId(), fulfillmentId).get();
        return ResponseEntity.ok(ApiResponse.success("Fulfillment pronto para retirada", mapFulfillment(updated)));
    }

    @GetMapping("/jobs")
    public ResponseEntity<ApiResponse<List<DeliveryJobResponse>>> listJobs(
            @RequestParam(name = "status", required = false) DeliveryJobStatus status
    ) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantContext ctx = tenantGuard.requireContext();
        List<DeliveryJob> list;
        if (status != null) {
            list = deliveryJobRepository.findByTenantIdAndStatusOrderByRequestedAtDesc(ctx.tenantId(), status);
        } else {
            list = deliveryJobRepository.findAll().stream()
                    .filter(j -> j.getTenant() != null && ctx.tenantId().equals(j.getTenant().getId()))
                    .toList();
        }
        List<DeliveryJobResponse> resp = list.stream().map(this::mapJob).toList();
        return ResponseEntity.ok(ApiResponse.success("DeliveryJobs listados", resp));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ApiResponse<DeliveryJobResponse>> getJob(@PathVariable Long jobId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantContext ctx = tenantGuard.requireContext();
        DeliveryJob j = deliveryJobRepository.findByTenantIdAndId(ctx.tenantId(), jobId)
                .orElseThrow(() -> new BusinessException("DELIVERY_JOB_NOT_FOUND"));
        return ResponseEntity.ok(ApiResponse.success("DeliveryJob encontrado", mapJob(j)));
    }

    @PostMapping("/jobs/{jobId}/report-issue")
    public ResponseEntity<ApiResponse<Void>> reportIssue(@PathVariable Long jobId, @RequestParam(name = "reason") String reason) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantContext ctx = tenantGuard.requireContext();
        // Since it's a tenant reporting issue, we pass courierUserId as null or bypass role check inside service
        deliveryJobService.reportIssue(ctx.tenantId(), jobId, null, reason);
        return ResponseEntity.ok(ApiResponse.success("Incidente reportado com sucesso", null));
    }

    private OrderFulfillmentResponse mapFulfillment(OrderFulfillment f) {
        return new OrderFulfillmentResponse(
                f.getId(),
                f.getPedido() != null ? f.getPedido().getId() : null,
                f.getFulfillmentType(),
                f.getStatus(),
                f.getCustomerName(),
                f.getCustomerPhoneMasked(),
                f.getDeliveryAddressText(),
                f.getDeliveryLatitude(),
                f.getDeliveryLongitude(),
                f.getDeliveryNotes(),
                f.getDeliveryRequestedAt(),
                f.getPickupReadyAt(),
                f.getCompletedAt(),
                f.getCancelledAt(),
                f.getCancellationReason()
        );
    }

    private DeliveryJobResponse mapJob(DeliveryJob j) {
        return new DeliveryJobResponse(
                j.getId(),
                j.getPedido() != null ? j.getPedido().getId() : null,
                j.getOrderFulfillment() != null ? j.getOrderFulfillment().getId() : null,
                j.getCourier() != null ? j.getCourier().getId() : null,
                j.getStatus(),
                j.getPickupAddressText(),
                j.getDeliveryAddressText(),
                j.getEstimatedDistanceKm(),
                j.getEstimatedDeliveryFee(),
                j.getFinalDeliveryFee(),
                j.getDeliveryFeeCurrency(),
                j.getDeliveryFeePaymentStatus(),
                j.getRequestedAt(),
                j.getAssignedAt(),
                j.getAcceptedAt(),
                j.getPickedUpAt(),
                j.getDeliveredAt(),
                j.getCancelledAt(),
                j.getFailedAt()
        );
    }
}
