package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.AvailablePaymentMethodResponse;
import com.restaurante.exception.DeviceUnauthorizedException;
import com.restaurante.financeiro.paymentmethod.service.TenantPaymentMethodService;
import com.restaurante.model.enums.PaymentDestination;
import com.restaurante.model.enums.PaymentUsageContext;
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

    private final TenantPaymentMethodService tenantPaymentMethodService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AvailablePaymentMethodResponse>>> list(
            @RequestParam PaymentDestination destination,
            HttpServletRequest http
    ) {
        DevicePrincipal device = requireDevicePrincipal();
        List<AvailablePaymentMethodResponse> methods = tenantPaymentMethodService
                .listAvailableForContext(device.tenantId(), PaymentUsageContext.DEVICE_POS, destination)
                .stream()
                .map(m -> {
                    AvailablePaymentMethodResponse r = new AvailablePaymentMethodResponse();
                    r.setCode(m.getCode());
                    r.setDisplayName(m.getDisplayName());
                    r.setDescription(m.getDescription());
                    r.setType(m.getType());
                    r.setConfirmationMode(m.getConfirmationMode());
                    r.setProvider(m.getProvider());
                    r.setRequiresOpenTurno(m.isRequiresOpenTurno());
                    r.setMinAmount(m.getMinAmount());
                    r.setMaxAmount(m.getMaxAmount());
                    r.setCurrency(m.getCurrency());
                    r.setSortOrder(m.getSortOrder());
                    r.setIconKey(m.getIconKey());
                    return r;
                })
                .toList();

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

