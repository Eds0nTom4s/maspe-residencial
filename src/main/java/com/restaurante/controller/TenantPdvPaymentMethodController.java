package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.AvailablePaymentMethodResponse;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.financeiro.paymentmethod.service.PaymentMethodPolicyResolutionService;
import com.restaurante.model.enums.PaymentDestination;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tenant/pdv")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TenantPdvPaymentMethodController {

    private final TenantGuard tenantGuard;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final PaymentMethodPolicyResolutionService policyResolutionService;

    @GetMapping("/payment-methods")
    public ResponseEntity<ApiResponse<List<AvailablePaymentMethodResponse>>> listPaymentMethods(
            @RequestParam Long unidadeAtendimentoId
    ) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER
        );
        TenantContext context = tenantGuard.requireContext();
        unidadeAtendimentoRepository.findByIdAndTenantId(unidadeAtendimentoId, context.tenantId())
                .filter(item -> Boolean.TRUE.equals(item.getAtiva()))
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        List<AvailablePaymentMethodResponse> methods = policyResolutionService.listEffectiveForTenantPdv(
                context.tenantId(), unidadeAtendimentoId, PaymentDestination.PEDIDO
        );
        return ResponseEntity.ok(ApiResponse.success("Métodos de pagamento disponíveis para o PDV", methods));
    }
}
