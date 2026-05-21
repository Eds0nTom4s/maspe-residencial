package com.restaurante.financeiro.paymentmethod;

import com.restaurante.dto.response.AvailablePaymentMethodResponse;
import com.restaurante.financeiro.paymentmethod.entity.DevicePaymentMethodPolicy;
import com.restaurante.financeiro.paymentmethod.entity.TenantPaymentMethod;
import com.restaurante.financeiro.paymentmethod.entity.UnidadePaymentMethodPolicy;
import com.restaurante.financeiro.paymentmethod.repository.DevicePaymentMethodPolicyRepository;
import com.restaurante.financeiro.paymentmethod.repository.TenantPaymentMethodRepository;
import com.restaurante.financeiro.paymentmethod.repository.UnidadePaymentMethodPolicyRepository;
import com.restaurante.financeiro.paymentmethod.service.PaymentMethodPolicyResolutionService;
import com.restaurante.financeiro.paymentmethod.service.TenantPaymentMethodService;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.*;
import com.restaurante.security.device.DevicePrincipal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentMethodPolicyResolutionServiceTest {

    @Test
    void unidade_block_removes_method_from_qr_list() {
        TenantPaymentMethodService tenantService = mock(TenantPaymentMethodService.class);
        TenantPaymentMethodRepository tenantRepo = mock(TenantPaymentMethodRepository.class);
        UnidadePaymentMethodPolicyRepository unidadeRepo = mock(UnidadePaymentMethodPolicyRepository.class);
        DevicePaymentMethodPolicyRepository deviceRepo = mock(DevicePaymentMethodPolicyRepository.class);

        TenantPaymentMethod cash = tenantMethod(PaymentMethodCode.CASH, true, true, true, true);
        when(tenantService.listAvailableForContext(eq(1L), eq(PaymentUsageContext.QR_PUBLICO), eq(PaymentDestination.PEDIDO)))
                .thenReturn(List.of(cash));
        when(unidadeRepo.findByTenant_IdAndUnidadeAtendimento_Id(eq(1L), eq(10L)))
                .thenReturn(List.of(unidadePolicy(PaymentMethodCode.CASH, PaymentMethodPolicyStatus.BLOCK, false)));

        PaymentMethodPolicyResolutionService svc = new PaymentMethodPolicyResolutionService(tenantService, tenantRepo, unidadeRepo, deviceRepo);
        List<AvailablePaymentMethodResponse> methods = svc.listEffectiveForQr(1L, 10L, PaymentDestination.PEDIDO);
        assertThat(methods).isEmpty();
    }

    @Test
    void device_canStartGateway_false_hides_gateway_method_in_device_list() {
        TenantPaymentMethodService tenantService = mock(TenantPaymentMethodService.class);
        TenantPaymentMethodRepository tenantRepo = mock(TenantPaymentMethodRepository.class);
        UnidadePaymentMethodPolicyRepository unidadeRepo = mock(UnidadePaymentMethodPolicyRepository.class);
        DevicePaymentMethodPolicyRepository deviceRepo = mock(DevicePaymentMethodPolicyRepository.class);

        TenantPaymentMethod appy = tenantMethod(PaymentMethodCode.APPYPAY, false, true, true, true);
        appy.setRequiresGateway(true);
        appy.setRequiresManualConfirmation(false);
        when(tenantService.listAvailableForContext(eq(1L), eq(PaymentUsageContext.DEVICE_POS), eq(PaymentDestination.PEDIDO)))
                .thenReturn(List.of(appy));
        when(unidadeRepo.findByTenant_IdAndUnidadeAtendimento_Id(eq(1L), eq(10L))).thenReturn(List.of());
        when(deviceRepo.findByTenant_IdAndDispositivoOperacional_Id(eq(1L), eq(99L)))
                .thenReturn(List.of(devicePolicy(PaymentMethodCode.APPYPAY, PaymentMethodPolicyStatus.ALLOW, false, false)));
        when(deviceRepo.findByTenant_IdAndDispositivoOperacional_IdAndPaymentMethodCode(eq(1L), eq(99L), eq(PaymentMethodCode.APPYPAY)))
                .thenReturn(Optional.of(devicePolicy(PaymentMethodCode.APPYPAY, PaymentMethodPolicyStatus.ALLOW, false, false)));

        DevicePrincipal device = new DevicePrincipal(
                99L, "POS", 1L, "T", 5L, 10L, null,
                DispositivoTipo.POS, DispositivoStatus.ATIVO, List.of(), 1
        );

        PaymentMethodPolicyResolutionService svc = new PaymentMethodPolicyResolutionService(tenantService, tenantRepo, unidadeRepo, deviceRepo);
        List<AvailablePaymentMethodResponse> methods = svc.listEffectiveForDevice(device, PaymentDestination.PEDIDO);
        assertThat(methods).isEmpty();
    }

    @Test
    void effective_limits_are_intersection_of_tenant_unidade_device() {
        TenantPaymentMethodService tenantService = mock(TenantPaymentMethodService.class);
        TenantPaymentMethodRepository tenantRepo = mock(TenantPaymentMethodRepository.class);
        UnidadePaymentMethodPolicyRepository unidadeRepo = mock(UnidadePaymentMethodPolicyRepository.class);
        DevicePaymentMethodPolicyRepository deviceRepo = mock(DevicePaymentMethodPolicyRepository.class);

        TenantPaymentMethod cash = tenantMethod(PaymentMethodCode.CASH, true, true, true, true);
        cash.setMinAmount(new BigDecimal("10.00"));
        cash.setMaxAmount(new BigDecimal("100.00"));
        when(tenantService.listAvailableForContext(eq(1L), eq(PaymentUsageContext.DEVICE_POS), eq(PaymentDestination.PEDIDO)))
                .thenReturn(List.of(cash));

        UnidadePaymentMethodPolicy up = unidadePolicy(PaymentMethodCode.CASH, PaymentMethodPolicyStatus.ALLOW, false);
        up.setMinAmount(new BigDecimal("20.00"));
        up.setMaxAmount(new BigDecimal("90.00"));
        when(unidadeRepo.findByTenant_IdAndUnidadeAtendimento_Id(eq(1L), eq(10L))).thenReturn(List.of(up));

        DevicePaymentMethodPolicy dp = devicePolicy(PaymentMethodCode.CASH, PaymentMethodPolicyStatus.ALLOW, true, true);
        dp.setMinAmount(new BigDecimal("30.00"));
        dp.setMaxAmount(new BigDecimal("80.00"));
        when(deviceRepo.findByTenant_IdAndDispositivoOperacional_Id(eq(1L), eq(99L))).thenReturn(List.of(dp));

        DevicePrincipal device = new DevicePrincipal(
                99L, "POS", 1L, "T", 5L, 10L, null,
                DispositivoTipo.POS, DispositivoStatus.ATIVO, List.of(), 1
        );

        PaymentMethodPolicyResolutionService svc = new PaymentMethodPolicyResolutionService(tenantService, tenantRepo, unidadeRepo, deviceRepo);
        List<AvailablePaymentMethodResponse> methods = svc.listEffectiveForDevice(device, PaymentDestination.PEDIDO);
        assertThat(methods).hasSize(1);
        assertThat(methods.get(0).getMinAmount()).isEqualByComparingTo("30.00");
        assertThat(methods.get(0).getMaxAmount()).isEqualByComparingTo("80.00");
    }

    @Test
    void validateGatewayStartDevice_blocks_when_device_policy_disallows_gateway_start() {
        TenantPaymentMethodService tenantService = mock(TenantPaymentMethodService.class);
        TenantPaymentMethodRepository tenantRepo = mock(TenantPaymentMethodRepository.class);
        UnidadePaymentMethodPolicyRepository unidadeRepo = mock(UnidadePaymentMethodPolicyRepository.class);
        DevicePaymentMethodPolicyRepository deviceRepo = mock(DevicePaymentMethodPolicyRepository.class);

        TenantPaymentMethod appy = tenantMethod(PaymentMethodCode.APPYPAY, false, true, true, true);
        appy.setRequiresGateway(true);
        when(tenantService.validateMethodAllowed(eq(1L), eq(PaymentMethodCode.APPYPAY), eq(PaymentUsageContext.DEVICE_POS), eq(PaymentDestination.PEDIDO), any()))
                .thenReturn(appy);
        when(tenantRepo.findByTenantIdAndCode(eq(1L), eq(PaymentMethodCode.APPYPAY))).thenReturn(Optional.of(appy));
        when(deviceRepo.findByTenant_IdAndDispositivoOperacional_Id(eq(1L), eq(99L)))
                .thenReturn(List.of(devicePolicy(PaymentMethodCode.APPYPAY, PaymentMethodPolicyStatus.ALLOW, false, false)));
        when(deviceRepo.findByTenant_IdAndDispositivoOperacional_IdAndPaymentMethodCode(eq(1L), eq(99L), eq(PaymentMethodCode.APPYPAY)))
                .thenReturn(Optional.of(devicePolicy(PaymentMethodCode.APPYPAY, PaymentMethodPolicyStatus.ALLOW, false, false)));

        DevicePrincipal device = new DevicePrincipal(
                99L, "POS", 1L, "T", 5L, 10L, null,
                DispositivoTipo.POS, DispositivoStatus.ATIVO, List.of(), 1
        );

        PaymentMethodPolicyResolutionService svc = new PaymentMethodPolicyResolutionService(tenantService, tenantRepo, unidadeRepo, deviceRepo);
        try {
            svc.validateGatewayStartDevice(device, PaymentMethodCode.APPYPAY, PaymentDestination.PEDIDO, new BigDecimal("50.00"));
        } catch (RuntimeException ex) {
            assertThat(ex.getMessage()).contains("gateway");
            return;
        }
        throw new AssertionError("Expected exception");
    }

    private TenantPaymentMethod tenantMethod(PaymentMethodCode code, boolean qr, boolean pos, boolean pedido, boolean fundo) {
        Tenant t = new Tenant();
        TenantPaymentMethod m = new TenantPaymentMethod();
        m.setTenant(t);
        m.setCode(code);
        m.setDisplayName(code.name());
        m.setStatus(PaymentMethodStatus.ACTIVE);
        m.setType(code == PaymentMethodCode.APPYPAY ? PaymentMethodType.DIGITAL_GATEWAY : PaymentMethodType.MANUAL_PHYSICAL);
        m.setConfirmationMode(code == PaymentMethodCode.APPYPAY ? PaymentConfirmationMode.GATEWAY_CALLBACK_POLLING : PaymentConfirmationMode.MANUAL_DEVICE);
        m.setProvider(code == PaymentMethodCode.APPYPAY ? PaymentMethodProvider.APPYPAY : PaymentMethodProvider.NONE);
        m.setEnabledForQr(qr);
        m.setEnabledForPos(pos);
        m.setEnabledForPedido(pedido);
        m.setEnabledForFundoConsumo(fundo);
        m.setRequiresGateway(code == PaymentMethodCode.APPYPAY);
        m.setRequiresManualConfirmation(code != PaymentMethodCode.APPYPAY);
        m.setSortOrder(10);
        return m;
    }

    private UnidadePaymentMethodPolicy unidadePolicy(PaymentMethodCode code, PaymentMethodPolicyStatus status, boolean inherit) {
        UnidadePaymentMethodPolicy p = new UnidadePaymentMethodPolicy();
        p.setPaymentMethodCode(code);
        p.setStatus(status);
        p.setInheritFromTenant(inherit);
        return p;
    }

    private DevicePaymentMethodPolicy devicePolicy(PaymentMethodCode code, PaymentMethodPolicyStatus status, boolean inherit, boolean canStartGateway) {
        DevicePaymentMethodPolicy p = new DevicePaymentMethodPolicy();
        p.setPaymentMethodCode(code);
        p.setStatus(status);
        p.setInheritFromUnidade(inherit);
        p.setCanStartGateway(canStartGateway);
        p.setCanConfirmManual(true);
        return p;
    }
}
