package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.AvailablePaymentMethodResponse;
import com.restaurante.exception.DeviceUnauthorizedException;
import com.restaurante.financeiro.paymentmethod.service.PaymentMethodPolicyResolutionService;
import com.restaurante.model.enums.PaymentDestination;
import com.restaurante.security.device.DevicePrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/device/payment-methods")
@RequiredArgsConstructor
@Tag(name = "Device Payment Methods", description = "Métodos de pagamento disponíveis para o device (tenant-aware)")
public class DevicePaymentMethodController {

    private final PaymentMethodPolicyResolutionService policyResolutionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AvailablePaymentMethodResponse>>> list(
            @RequestParam PaymentDestination destination,
            HttpServletRequest http
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        List<AvailablePaymentMethodResponse> methods = policyResolutionService.listEffectiveForDevice(device, destination);

        return ResponseEntity.ok(ApiResponse.success("Métodos de pagamento disponíveis", methods));
    }

    private DevicePrincipal requireDevicePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof DevicePrincipal device)) {
            throw new DeviceUnauthorizedException("DevicePrincipal ausente.");
        }
        return device;
    }
}
