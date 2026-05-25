package com.restaurante.delivery.service;

import com.restaurante.delivery.repository.CourierProfileRepository;
import com.restaurante.delivery.repository.DeliveryCourierInviteRepository;
import com.restaurante.delivery.repository.DeliveryJobRepository;
import com.restaurante.delivery.repository.OrderFulfillmentRepository;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.*;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.service.operacional.OperationalEventLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DeliveryJobServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private PedidoRepository pedidoRepository;
    @Mock private OrderFulfillmentRepository orderFulfillmentRepository;
    @Mock private DeliveryJobRepository deliveryJobRepository;
    @Mock private DeliveryCourierInviteRepository inviteRepository;
    @Mock private CourierProfileRepository courierProfileRepository;
    @Mock private DeliveryCourierMatchingService matchingService;
    @Mock private TenantDeliveryPolicyService tenantDeliveryPolicyService;
    @Mock private OperationalEventLogService operationalEventLogService;

    private DeliveryJobService service;

    @BeforeEach
    void setUp() {
        service = new DeliveryJobService(
                tenantRepository, pedidoRepository, orderFulfillmentRepository,
                deliveryJobRepository, inviteRepository, courierProfileRepository,
                matchingService, tenantDeliveryPolicyService, operationalEventLogService
        );
    }

    @Test
    void createJobAndProgressToInvitedStateSuccessfully() {
        Long tenantId = 1L;
        Long pedidoId = 100L;

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        Pedido pedido = new Pedido();
        pedido.setId(pedidoId);

        OrderFulfillment fulfillment = new OrderFulfillment();
        fulfillment.setFulfillmentType(FulfillmentType.CONSUMA_NETWORK_DELIVERY);
        fulfillment.setStatus(OrderFulfillmentStatus.REQUESTED);

        TenantDeliveryPolicy policy = new TenantDeliveryPolicy();
        policy.setStatus(TenantDeliveryPolicyStatus.ACTIVE);
        policy.setMaxDeliveryDistanceKm(BigDecimal.valueOf(10));

        CourierProfile candidate = new CourierProfile();
        candidate.setId(20L);

        when(deliveryJobRepository.findByTenantIdAndPedido_Id(tenantId, pedidoId)).thenReturn(Optional.empty());
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedido));
        when(orderFulfillmentRepository.findByTenantIdAndPedido_Id(tenantId, pedidoId)).thenReturn(Optional.of(fulfillment));
        when(tenantDeliveryPolicyService.getOrCreateDefault(tenantId)).thenReturn(policy);
        when(deliveryJobRepository.save(any())).thenAnswer(inv -> {
            DeliveryJob dj = inv.getArgument(0);
            dj.setId(50L);
            return dj;
        });
        when(deliveryJobRepository.findByTenantIdAndId(eq(tenantId), any())).thenAnswer(inv -> {
            DeliveryJob j = new DeliveryJob();
            j.setId(50L);
            j.setTenant(tenant);
            j.setPedido(pedido);
            j.setOrderFulfillment(fulfillment);
            j.setStatus(DeliveryJobStatus.CREATED);
            j.setPickupLatitude(BigDecimal.valueOf(-8));
            j.setPickupLongitude(BigDecimal.valueOf(13));
            return Optional.of(j);
        });

        when(matchingService.findMatchingCouriers(eq(tenantId), eq(pedidoId), any(), any(), any()))
                .thenReturn(List.of(candidate));

        // Act
        DeliveryJob job = service.createJobForFulfillment(tenantId, pedidoId);

        // Assert
        verify(deliveryJobRepository, atLeastOnce()).save(any(DeliveryJob.class));
        verify(inviteRepository, times(1)).save(any(DeliveryCourierInvite.class));
    }

    @Test
    void acceptInviteTransitionsStatesAndUpdatesCourier() {
        Long tenantId = 1L;
        Long inviteId = 200L;
        Long courierUserId = 5L;

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);

        User courierUser = new User();
        courierUser.setId(courierUserId);

        CourierProfile courier = new CourierProfile();
        courier.setId(10L);
        courier.setCourierUser(courierUser);
        courier.setStatus(CourierStatus.ACTIVE);
        courier.setVerificationStatus(CourierVerificationStatus.VERIFIED);

        DeliveryJob job = new DeliveryJob();
        job.setId(50L);
        job.setTenant(tenant);
        job.setStatus(DeliveryJobStatus.COURIER_INVITED);
        
        OrderFulfillment fulfillment = new OrderFulfillment();
        fulfillment.setStatus(OrderFulfillmentStatus.REQUESTED);
        job.setOrderFulfillment(fulfillment);

        DeliveryCourierInvite invite = new DeliveryCourierInvite();
        invite.setId(inviteId);
        invite.setTenant(tenant);
        invite.setCourier(courier);
        invite.setDeliveryJob(job);
        invite.setStatus(DeliveryCourierInviteStatus.PENDING);
        invite.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(inviteRepository.findByTenantIdAndId(tenantId, inviteId)).thenReturn(Optional.of(invite));

        // Act
        service.acceptInvite(tenantId, inviteId, courierUserId);

        // Assert
        assertThat(invite.getStatus()).isEqualTo(DeliveryCourierInviteStatus.ACCEPTED);
        assertThat(job.getStatus()).isEqualTo(DeliveryJobStatus.COURIER_ACCEPTED);
        assertThat(job.getCourier()).isEqualTo(courier);
        assertThat(fulfillment.getStatus()).isEqualTo(OrderFulfillmentStatus.COURIER_ASSIGNED);
        assertThat(courier.getCurrentAvailability()).isEqualTo(CourierAvailability.ONLINE_BUSY);
        assertThat(courier.getActiveDeliveryJobId()).isEqualTo(job.getId());

        verify(inviteRepository).save(invite);
        verify(deliveryJobRepository).save(job);
        verify(orderFulfillmentRepository).save(fulfillment);
        verify(courierProfileRepository).save(courier);
    }

    @Test
    void acceptInviteThrowsIfInviteAlreadyAccepted() {
        Long tenantId = 1L;
        Long inviteId = 200L;
        Long courierUserId = 5L;

        DeliveryCourierInvite invite = new DeliveryCourierInvite();
        invite.setStatus(DeliveryCourierInviteStatus.ACCEPTED);

        when(inviteRepository.findByTenantIdAndId(tenantId, inviteId)).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> service.acceptInvite(tenantId, inviteId, courierUserId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("DELIVERY_INVITE_ALREADY_ACCEPTED");
    }

    @Test
    void customerCancellationThrowsIfPolicyNeverAfterPayment() {
        Long tenantId = 1L;
        Long pedidoId = 100L;

        TenantDeliveryPolicy policy = new TenantDeliveryPolicy();
        policy.setCancelAllowedUntilStatus(DeliveryCancelAllowedUntilStatus.NEVER_AFTER_PAYMENT);

        DeliveryJob job = new DeliveryJob();
        job.setStatus(DeliveryJobStatus.CREATED);

        when(deliveryJobRepository.findByTenantIdAndPedido_Id(tenantId, pedidoId)).thenReturn(Optional.of(job));
        when(tenantDeliveryPolicyService.getOrCreateDefault(tenantId)).thenReturn(policy);

        assertThatThrownBy(() -> service.cancelByCustomer(tenantId, pedidoId, "Change of plans"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("DELIVERY_CANCEL_NOT_ALLOWED");
    }
}
