package com.restaurante.financeiro.paymentmethod;

import com.restaurante.exception.BusinessException;
import com.restaurante.financeiro.gateway.appypay.AppyPayProperties;
import com.restaurante.financeiro.paymentmethod.entity.TenantPaymentMethod;
import com.restaurante.financeiro.paymentmethod.repository.TenantPaymentMethodRepository;
import com.restaurante.financeiro.paymentmethod.service.TenantPaymentMethodBootstrapService;
import com.restaurante.financeiro.paymentmethod.service.TenantPaymentMethodService;
import com.restaurante.model.enums.PaymentConfirmationMode;
import com.restaurante.model.enums.PaymentDestination;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodProvider;
import com.restaurante.model.enums.PaymentMethodStatus;
import com.restaurante.model.enums.PaymentMethodType;
import com.restaurante.model.enums.PaymentUsageContext;
import com.restaurante.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantPaymentMethodValidationTest {

    @Test
    void suspended_is_blocked() {
        TenantPaymentMethodRepository repo = mock(TenantPaymentMethodRepository.class);
        TenantPaymentMethodBootstrapService bootstrap = mock(TenantPaymentMethodBootstrapService.class);
        AppyPayProperties appy = mock(AppyPayProperties.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        when(appy.isMock()).thenReturn(true);

        TenantPaymentMethodService service = new TenantPaymentMethodService(repo, bootstrap, appy, tenantRepository);

        TenantPaymentMethod m = new TenantPaymentMethod();
        m.setCode(PaymentMethodCode.CASH);
        m.setStatus(PaymentMethodStatus.SUSPENDED);
        m.setType(PaymentMethodType.MANUAL_PHYSICAL);
        m.setConfirmationMode(PaymentConfirmationMode.MANUAL_DEVICE);
        m.setProvider(PaymentMethodProvider.NONE);
        m.setEnabledForQr(true);
        m.setEnabledForPedido(true);
        when(repo.findByTenantIdAndCode(1L, PaymentMethodCode.CASH)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.validateMethodAllowed(1L, PaymentMethodCode.CASH, PaymentUsageContext.QR_PUBLICO, PaymentDestination.PEDIDO, new BigDecimal("10.00")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void min_max_are_enforced() {
        TenantPaymentMethodRepository repo = mock(TenantPaymentMethodRepository.class);
        TenantPaymentMethodBootstrapService bootstrap = mock(TenantPaymentMethodBootstrapService.class);
        AppyPayProperties appy = mock(AppyPayProperties.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        when(appy.isMock()).thenReturn(true);

        TenantPaymentMethodService service = new TenantPaymentMethodService(repo, bootstrap, appy, tenantRepository);

        TenantPaymentMethod m = new TenantPaymentMethod();
        m.setCode(PaymentMethodCode.CASH);
        m.setStatus(PaymentMethodStatus.ACTIVE);
        m.setType(PaymentMethodType.MANUAL_PHYSICAL);
        m.setConfirmationMode(PaymentConfirmationMode.MANUAL_DEVICE);
        m.setProvider(PaymentMethodProvider.NONE);
        m.setEnabledForQr(true);
        m.setEnabledForPedido(true);
        m.setMinAmount(new BigDecimal("10.00"));
        m.setMaxAmount(new BigDecimal("20.00"));
        when(repo.findByTenantIdAndCode(1L, PaymentMethodCode.CASH)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.validateMethodAllowed(1L, PaymentMethodCode.CASH, PaymentUsageContext.QR_PUBLICO, PaymentDestination.PEDIDO, new BigDecimal("9.99")))
                .isInstanceOf(BusinessException.class);
        service.validateMethodAllowed(1L, PaymentMethodCode.CASH, PaymentUsageContext.QR_PUBLICO, PaymentDestination.PEDIDO, new BigDecimal("10.00"));
        service.validateMethodAllowed(1L, PaymentMethodCode.CASH, PaymentUsageContext.QR_PUBLICO, PaymentDestination.PEDIDO, new BigDecimal("20.00"));
        assertThatThrownBy(() -> service.validateMethodAllowed(1L, PaymentMethodCode.CASH, PaymentUsageContext.QR_PUBLICO, PaymentDestination.PEDIDO, new BigDecimal("20.01")))
                .isInstanceOf(BusinessException.class);
    }
}
