package com.restaurante.delivery.controller;

import com.restaurante.delivery.dto.request.CustomerCancelDeliveryRequest;
import com.restaurante.delivery.dto.request.PublicOrderFulfillmentRequest;
import com.restaurante.delivery.dto.response.OrderFulfillmentResponse;
import com.restaurante.delivery.dto.response.PublicDeliveryOptionsResponse;
import com.restaurante.delivery.service.DeliveryJobService;
import com.restaurante.delivery.service.OrderFulfillmentService;
import com.restaurante.delivery.service.ProductDeliveryPolicyService;
import com.restaurante.delivery.service.TenantDeliveryPolicyService;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.OrderFulfillment;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.DeliveryMode;
import com.restaurante.model.enums.FulfillmentType;
import com.restaurante.repository.TenantRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
@Tag(name = "Guest Client - Delivery & Fulfillment", description = "Endpoints publicos de entrega para clientes da Consuma (QR/link)")
public class PublicDeliveryController {

    private final TenantRepository tenantRepository;
    private final TenantDeliveryPolicyService tenantDeliveryPolicyService;
    private final ProductDeliveryPolicyService productDeliveryPolicyService;
    private final OrderFulfillmentService orderFulfillmentService;
    private final DeliveryJobService deliveryJobService;

    @GetMapping("/tenants/{tenantCode}/delivery/options")
    public ResponseEntity<ApiResponse<PublicDeliveryOptionsResponse>> getDeliveryOptions(@PathVariable String tenantCode) {
        Tenant tenant = tenantRepository.findByTenantCode(tenantCode)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND"));

        var policy = tenantDeliveryPolicyService.getOrCreateDefault(tenant.getId());

        List<FulfillmentType> availableTypes = new ArrayList<>();
        if (policy.isAllowCustomerPickup()) {
            availableTypes.add(FulfillmentType.CUSTOMER_PICKUP);
        }
        if (policy.isDeliveryEnabled()) {
            if (policy.getDeliveryMode() == DeliveryMode.CONSUMA_NETWORK || policy.getDeliveryMode() == DeliveryMode.HYBRID) {
                availableTypes.add(FulfillmentType.CONSUMA_NETWORK_DELIVERY);
            }
            if (policy.getDeliveryMode() == DeliveryMode.TENANT_OWN_DELIVERY || policy.getDeliveryMode() == DeliveryMode.HYBRID) {
                availableTypes.add(FulfillmentType.TENANT_DELIVERY);
            }
        }

        Map<Long, Boolean> productEligibility = new HashMap<>();
        productDeliveryPolicyService.list(tenant.getId()).forEach(p -> {
            if (p.getProductId() != null) {
                productEligibility.put(p.getProductId(), p.isDeliveryEligible());
            }
        });

        PublicDeliveryOptionsResponse resp = new PublicDeliveryOptionsResponse(
                policy.isDeliveryEnabled(),
                availableTypes,
                productEligibility,
                policy.isAllowCustomerPickup()
        );

        return ResponseEntity.ok(ApiResponse.success("Opcoes de entrega listadas", resp));
    }

    @PostMapping("/orders/{pedidoId}/fulfillment")
    public ResponseEntity<ApiResponse<OrderFulfillmentResponse>> submitFulfillment(
            @RequestParam(name = "tenantId") Long tenantId,
            @PathVariable Long pedidoId,
            @Valid @RequestBody PublicOrderFulfillmentRequest request
    ) {
        OrderFulfillmentResponse resp = orderFulfillmentService.createOrUpdatePublic(tenantId, pedidoId, request);
        return ResponseEntity.ok(ApiResponse.success("Fulfillment registrado com sucesso", resp));
    }

    @PostMapping("/delivery/jobs/{jobId}/cancel-by-customer")
    public ResponseEntity<ApiResponse<Void>> cancelByCustomer(
            @RequestParam(name = "tenantId") Long tenantId,
            @PathVariable Long jobId,
            @RequestParam(name = "pedidoId") Long pedidoId,
            @Valid @RequestBody CustomerCancelDeliveryRequest request
    ) {
        deliveryJobService.cancelByCustomer(tenantId, pedidoId, request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Entrega cancelada pelo cliente", null));
    }
}
