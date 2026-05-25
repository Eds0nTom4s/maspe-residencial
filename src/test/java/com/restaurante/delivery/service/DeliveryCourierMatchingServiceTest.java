package com.restaurante.delivery.service;

import com.restaurante.delivery.repository.CourierProfileRepository;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.*;
import com.restaurante.repository.ItemPedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DeliveryCourierMatchingServiceTest {

    @Mock
    private CourierProfileRepository courierProfileRepository;
    @Mock
    private ItemPedidoRepository itemPedidoRepository;
    @Mock
    private ProductDeliveryPolicyService productDeliveryPolicyService;

    private DeliveryCourierMatchingService service;

    @BeforeEach
    void setUp() {
        service = new DeliveryCourierMatchingService(courierProfileRepository, itemPedidoRepository, productDeliveryPolicyService);
    }

    @Test
    void matchesCouriersSuccessfullyBasedOnDistanceAndVehicleCompatibility() {
        Long tenantId = 1L;
        Long pedidoId = 100L;
        BigDecimal pickupLat = new BigDecimal("-8.836800");
        BigDecimal pickupLng = new BigDecimal("13.234300");
        BigDecimal maxDistanceKm = BigDecimal.valueOf(10);

        // Courier A: online, motorbike, 2km away (Luanda center)
        CourierProfile courierA = new CourierProfile();
        courierA.setId(10L);
        courierA.setStatus(CourierStatus.ACTIVE);
        courierA.setVerificationStatus(CourierVerificationStatus.VERIFIED);
        courierA.setCurrentAvailability(CourierAvailability.ONLINE_AVAILABLE);
        courierA.setVehicleType(CourierVehicleType.MOTORBIKE);
        courierA.setCurrentLatitude(new BigDecimal("-8.826800")); // approx 1.1km north
        courierA.setCurrentLongitude(new BigDecimal("13.234300"));

        // Courier B: online, car, 5km away
        CourierProfile courierB = new CourierProfile();
        courierB.setId(20L);
        courierB.setStatus(CourierStatus.ACTIVE);
        courierB.setVerificationStatus(CourierVerificationStatus.VERIFIED);
        courierB.setCurrentAvailability(CourierAvailability.ONLINE_AVAILABLE);
        courierB.setVehicleType(CourierVehicleType.CAR);
        courierB.setCurrentLatitude(new BigDecimal("-8.791800")); // approx 5km north
        courierB.setCurrentLongitude(new BigDecimal("13.234300"));

        // Courier C: 12km away (exceeds limit)
        CourierProfile courierC = new CourierProfile();
        courierC.setId(30L);
        courierC.setStatus(CourierStatus.ACTIVE);
        courierC.setVerificationStatus(CourierVerificationStatus.VERIFIED);
        courierC.setCurrentAvailability(CourierAvailability.ONLINE_AVAILABLE);
        courierC.setVehicleType(CourierVehicleType.MOTORBIKE);
        courierC.setCurrentLatitude(new BigDecimal("-8.700000"));
        courierC.setCurrentLongitude(new BigDecimal("13.230000"));

        when(courierProfileRepository.findAvailableNearbyCandidates(any(), any(), any(), any()))
                .thenReturn(List.of(courierA, courierB, courierC));

        // Order contains a normal item
        Produto produto = new Produto();
        produto.setId(500L);
        ItemPedido item = new ItemPedido();
        item.setProduto(produto);
        when(itemPedidoRepository.findByPedidoId(pedidoId)).thenReturn(List.of(item));
        when(productDeliveryPolicyService.getOrNull(tenantId, 500L)).thenReturn(null);

        List<CourierProfile> results = service.findMatchingCouriers(tenantId, pedidoId, pickupLat, pickupLng, maxDistanceKm);

        // Should return closest first (Courier A then Courier B) and filter out Courier C because of distance
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getId()).isEqualTo(10L);
        assertThat(results.get(1).getId()).isEqualTo(20L);
    }

    @Test
    void filtersOutCouriersIfVehicleTypeNotEligibleByProductPolicy() {
        Long tenantId = 1L;
        Long pedidoId = 100L;
        BigDecimal pickupLat = new BigDecimal("-8.836800");
        BigDecimal pickupLng = new BigDecimal("13.234300");
        BigDecimal maxDistanceKm = BigDecimal.valueOf(10);

        CourierProfile courierA = new CourierProfile();
        courierA.setId(10L);
        courierA.setStatus(CourierStatus.ACTIVE);
        courierA.setVerificationStatus(CourierVerificationStatus.VERIFIED);
        courierA.setCurrentAvailability(CourierAvailability.ONLINE_AVAILABLE);
        courierA.setVehicleType(CourierVehicleType.MOTORBIKE);
        courierA.setCurrentLatitude(new BigDecimal("-8.826800"));
        courierA.setCurrentLongitude(new BigDecimal("13.234300"));

        CourierProfile courierB = new CourierProfile();
        courierB.setId(20L);
        courierB.setStatus(CourierStatus.ACTIVE);
        courierB.setVerificationStatus(CourierVerificationStatus.VERIFIED);
        courierB.setCurrentAvailability(CourierAvailability.ONLINE_AVAILABLE);
        courierB.setVehicleType(CourierVehicleType.CAR);
        courierB.setCurrentLatitude(new BigDecimal("-8.826800"));
        courierB.setCurrentLongitude(new BigDecimal("13.234300"));

        when(courierProfileRepository.findAvailableNearbyCandidates(any(), any(), any(), any()))
                .thenReturn(List.of(courierA, courierB));

        // Order contains product that cannot be delivered by Motorbike (fragile item)
        Produto produto = new Produto();
        produto.setId(500L);
        ItemPedido item = new ItemPedido();
        item.setProduto(produto);
        when(itemPedidoRepository.findByPedidoId(pedidoId)).thenReturn(List.of(item));

        ProductDeliveryPolicy productPolicy = new ProductDeliveryPolicy();
        productPolicy.setAllowMotorbikeDelivery(false);
        productPolicy.setAllowCarDelivery(true);
        productPolicy.setMaxDeliveryDistanceKm(BigDecimal.valueOf(10));
        when(productDeliveryPolicyService.getOrNull(tenantId, 500L)).thenReturn(productPolicy);

        List<CourierProfile> results = service.findMatchingCouriers(tenantId, pedidoId, pickupLat, pickupLng, maxDistanceKm);

        // Only Courier B (Car) should be returned because Motorbike is disallowed
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(20L);
    }
}
