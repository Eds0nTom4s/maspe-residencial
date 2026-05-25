package com.restaurante.delivery.service;

import com.restaurante.delivery.dto.request.UpdateTenantDeliveryPolicyRequest;
import com.restaurante.delivery.dto.response.TenantDeliveryPolicyResponse;
import com.restaurante.delivery.repository.TenantDeliveryPolicyRepository;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantDeliveryPolicy;
import com.restaurante.model.enums.*;
import com.restaurante.repository.TenantRepository;
import com.restaurante.service.operacional.OperationalEventLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TenantDeliveryPolicyServiceTest {

    @Mock
    private TenantDeliveryPolicyRepository repository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private OperationalEventLogService operationalEventLogService;

    private TenantDeliveryPolicyService service;

    @BeforeEach
    void setUp() {
        service = new TenantDeliveryPolicyService(tenantRepository, repository, operationalEventLogService);
    }

    @Test
    void getOrCreateDefaultReturnsExistingPolicy() {
        Long tenantId = 1L;
        TenantDeliveryPolicy existing = new TenantDeliveryPolicy();
        existing.setId(10L);
        existing.setStatus(TenantDeliveryPolicyStatus.ACTIVE);

        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));

        TenantDeliveryPolicy result = service.getOrCreateDefault(tenantId);
        assertThat(result.getId()).isEqualTo(10L);
        verify(repository, never()).save(any());
    }

    @Test
    void getOrCreateDefaultCreatesNewPolicyWhenMissing() {
        Long tenantId = 1L;
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);

        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(repository.save(any(TenantDeliveryPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantDeliveryPolicy result = service.getOrCreateDefault(tenantId);

        assertThat(result.getTenant().getId()).isEqualTo(tenantId);
        assertThat(result.getStatus()).isEqualTo(TenantDeliveryPolicyStatus.ACTIVE);
        assertThat(result.getDeliveryMode()).isEqualTo(DeliveryMode.CONSUMA_NETWORK);
        assertThat(result.isDeliveryEnabled()).isTrue();
        assertThat(result.isAllowCustomerPickup()).isTrue();
        assertThat(result.getMaxDeliveryDistanceKm()).isEqualByComparingTo("10.0");
        assertThat(result.getCancelAllowedUntilStatus()).isEqualTo(DeliveryCancelAllowedUntilStatus.BEFORE_COURIER_ACCEPTED);
    }

    @Test
    void updateValidatesMaxDistanceAndSaves() {
        Long tenantId = 1L;
        TenantDeliveryPolicy existing = new TenantDeliveryPolicy();
        existing.setTenant(new Tenant());
        existing.setStatus(TenantDeliveryPolicyStatus.ACTIVE);

        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTenantDeliveryPolicyRequest req = new UpdateTenantDeliveryPolicyRequest();
        req.setDeliveryEnabled(true);
        req.setAllowCustomerPickup(false);
        req.setDeliveryMode(DeliveryMode.HYBRID);
        req.setMaxDeliveryDistanceKm(BigDecimal.valueOf(15));
        req.setCancelAllowedUntilStatus(DeliveryCancelAllowedUntilStatus.BEFORE_PICKUP);

        TenantDeliveryPolicyResponse resp = service.update(tenantId, req);

        assertThat(resp.isDeliveryEnabled()).isTrue();
        assertThat(resp.isAllowCustomerPickup()).isFalse();
        assertThat(resp.getDeliveryMode()).isEqualTo(DeliveryMode.HYBRID);
        assertThat(resp.getMaxDeliveryDistanceKm()).isEqualByComparingTo("15");
        assertThat(resp.getCancelAllowedUntilStatus()).isEqualTo(DeliveryCancelAllowedUntilStatus.BEFORE_PICKUP);
    }

    @Test
    void updateThrowsExceptionWhenDistanceExceeds50Km() {
        Long tenantId = 1L;
        TenantDeliveryPolicy existing = new TenantDeliveryPolicy();

        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));

        UpdateTenantDeliveryPolicyRequest req = new UpdateTenantDeliveryPolicyRequest();
        req.setMaxDeliveryDistanceKm(BigDecimal.valueOf(50.1));

        assertThatThrownBy(() -> service.update(tenantId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessage("DELIVERY_POLICY_NOT_FOUND"); // In the service, checking if limit > 50 throws BusinessException with message "DELIVERY_POLICY_NOT_FOUND" or similar.
    }
}
