package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.DeviceErrorResponse;
import com.restaurante.dto.response.DeviceProductAvailabilityResponse;
import com.restaurante.exception.DeviceApiException;
import com.restaurante.inventory.service.ProductAvailabilityService;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.security.device.DevicePrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/device/inventory")
@RequiredArgsConstructor
@Tag(name = "Device - Inventory", description = "Consulta de disponibilidade de produtos (somente leitura)")
public class DeviceInventoryController {

    private final ProductAvailabilityService availabilityService;

    @GetMapping("/product-availability/{productId}")
    public ResponseEntity<ApiResponse<DeviceProductAvailabilityResponse>> availability(@PathVariable Long productId) {
        DevicePrincipal device = requireDevicePrincipal();
        if (device.capabilities() == null || !device.capabilities().contains(DeviceCapability.VIEW_PRODUCT_AVAILABILITY)) {
            throw new DeviceApiException(
                    HttpStatus.FORBIDDEN,
                    DeviceErrorResponse.DeviceErrorCode.DEVICE_FORBIDDEN,
                    "Capability requerida: VIEW_PRODUCT_AVAILABILITY",
                    false,
                    DeviceErrorResponse.DeviceRecoveryAction.NONE,
                    java.util.Map.of("requiredCapability", DeviceCapability.VIEW_PRODUCT_AVAILABILITY.name())
            );
        }

        var result = availabilityService.availabilityForProduct(device.tenantId(), productId);
        DeviceProductAvailabilityResponse r = new DeviceProductAvailabilityResponse();
        r.setProductId(productId);
        r.setAvailable(result.available());
        r.setEstimatedAvailableQuantity(result.estimatedAvailableQuantity());
        r.setStockPolicy(result.stockPolicy());
        r.setWarning(null);
        return ResponseEntity.ok(ApiResponse.success("Availability", r));
    }

    private DevicePrincipal requireDevicePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;
        if (principal instanceof DevicePrincipal dp) return dp;
        throw new IllegalStateException("DevicePrincipal obrigatório.");
    }
}

