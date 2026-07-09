package com.restaurante.delivery.service;

import com.restaurante.consumo.identificacao.service.TelefoneNormalizerService;
import com.restaurante.delivery.dto.request.PublicOrderFulfillmentRequest;
import com.restaurante.delivery.dto.response.OrderFulfillmentResponse;
import com.restaurante.delivery.repository.OrderFulfillmentRepository;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.ItemPedido;
import com.restaurante.model.entity.OrderFulfillment;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.DeliveryMode;
import com.restaurante.model.enums.FulfillmentType;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.OrderFulfillmentStatus;
import com.restaurante.model.enums.TenantDeliveryPolicyStatus;
import com.restaurante.repository.ItemPedidoRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderFulfillmentService {

    private final TenantRepository tenantRepository;
    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final OrderFulfillmentRepository repository;
    private final TenantDeliveryPolicyService tenantDeliveryPolicyService;
    private final ProductDeliveryPolicyService productDeliveryPolicyService;
    private final TelefoneNormalizerService telefoneNormalizerService;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public OrderFulfillmentResponse createOrUpdatePublic(Long tenantId, Long pedidoId, PublicOrderFulfillmentRequest req) {
        if (tenantId == null || pedidoId == null) throw new BusinessException("FULFILLMENT_NOT_FOUND");
        if (req == null || req.getFulfillmentType() == null) throw new BusinessException("FULFILLMENT_INVALID_STATE");

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND"));
        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow(() -> new BusinessException("PEDIDO_NOT_FOUND"));
        if (pedido.getTenant() == null || !tenantId.equals(pedido.getTenant().getId())) throw new BusinessException("DELIVERY_FORBIDDEN");

        var policy = tenantDeliveryPolicyService.getOrCreateDefault(tenantId);
        if (policy.getStatus() != TenantDeliveryPolicyStatus.ACTIVE) throw new BusinessException("DELIVERY_POLICY_DISABLED");

        validateEligibility(tenantId, pedidoId, req.getFulfillmentType(), policy.getDeliveryMode(), policy.isDeliveryEnabled());

        OrderFulfillment f = repository.findByTenantIdAndPedido_Id(tenantId, pedidoId).orElse(null);
        boolean creating = false;
        if (f == null) {
            f = new OrderFulfillment();
            f.setTenant(tenant);
            f.setPedido(pedido);
            f.setStatus(OrderFulfillmentStatus.REQUESTED);
            creating = true;
        } else {
            if (f.getStatus() == OrderFulfillmentStatus.CANCELLED || f.getStatus() == OrderFulfillmentStatus.DELIVERED) {
                throw new BusinessException("FULFILLMENT_INVALID_STATE");
            }
        }

        f.setFulfillmentType(req.getFulfillmentType());
        f.setCustomerName(req.getCustomerName());
        if (req.getCustomerPhone() != null && !req.getCustomerPhone().isBlank()) {
            String normalized = telefoneNormalizerService.normalizeOrThrow(req.getCustomerPhone());
            f.setCustomerPhoneMasked(telefoneNormalizerService.mask(normalized));
        }
        f.setDeliveryAddressText(req.getDeliveryAddressText());
        f.setDeliveryLatitude(req.getDeliveryLatitude());
        f.setDeliveryLongitude(req.getDeliveryLongitude());
        f.setDeliveryNotes(req.getDeliveryNotes());
        f.setDeliveryRequestedAt(LocalDateTime.now());
        f = repository.save(f);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                creating ? OperationalEventType.ORDER_FULFILLMENT_CREATED : OperationalEventType.ORDER_FULFILLMENT_UPDATED,
                OperationalEntityType.ORDER_FULFILLMENT,
                f.getId(),
                OperationalOrigem.QR_PUBLICO,
                creating ? "OrderFulfillment criado" : "OrderFulfillment atualizado",
                Map.of(
                        "tenantId", tenantId,
                        "pedidoId", pedidoId,
                        "fulfillmentType", f.getFulfillmentType() != null ? f.getFulfillmentType().name() : null,
                        "status", f.getStatus() != null ? f.getStatus().name() : null
                ),
                null,
                null
        );

        return map(f);
    }

    @Transactional
    public OrderFulfillment markReadyForPickup(Long tenantId, Long fulfillmentId) {
        OrderFulfillment f = repository.findByTenantIdAndId(tenantId, fulfillmentId).orElseThrow(() -> new BusinessException("FULFILLMENT_NOT_FOUND"));
        if (f.getStatus() == OrderFulfillmentStatus.CANCELLED || f.getStatus() == OrderFulfillmentStatus.DELIVERED) {
            throw new BusinessException("FULFILLMENT_INVALID_STATE");
        }
        f.setStatus(OrderFulfillmentStatus.READY_FOR_PICKUP);
        f.setPickupReadyAt(LocalDateTime.now());
        f = repository.save(f);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.DELIVERY_PICKUP_READY,
                OperationalEntityType.ORDER_FULFILLMENT,
                f.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Fulfillment marcado como pronto para pickup",
                Map.of("tenantId", tenantId, "fulfillmentId", f.getId(), "pedidoId", f.getPedido() != null ? f.getPedido().getId() : null),
                null,
                null
        );
        return f;
    }

    @Transactional
    public OrderFulfillment cancelByCustomer(Long tenantId, Long pedidoId, String reason) {
        OrderFulfillment f = repository.findByTenantIdAndPedido_Id(tenantId, pedidoId).orElseThrow(() -> new BusinessException("FULFILLMENT_NOT_FOUND"));
        if (f.getStatus() == OrderFulfillmentStatus.CANCELLED || f.getStatus() == OrderFulfillmentStatus.DELIVERED) {
            throw new BusinessException("DELIVERY_CANCEL_NOT_ALLOWED");
        }
        f.setStatus(OrderFulfillmentStatus.CANCELLED);
        f.setCancelledAt(LocalDateTime.now());
        f.setCancellationReason(reason);
        f = repository.save(f);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.DELIVERY_CANCELLED_BY_CUSTOMER,
                OperationalEntityType.ORDER_FULFILLMENT,
                f.getId(),
                OperationalOrigem.QR_PUBLICO,
                "Fulfillment cancelado pelo cliente",
                Map.of("tenantId", tenantId, "pedidoId", pedidoId),
                null,
                null
        );
        return f;
    }

    @Transactional(readOnly = true)
    public OrderFulfillment getOrNull(Long tenantId, Long pedidoId) {
        if (tenantId == null || pedidoId == null) return null;
        return repository.findByTenantIdAndPedido_Id(tenantId, pedidoId).orElse(null);
    }

    private void validateEligibility(Long tenantId, Long pedidoId, FulfillmentType type, DeliveryMode deliveryMode, boolean deliveryEnabled) {
        if (type == FulfillmentType.CUSTOMER_PICKUP) return;

        if (!deliveryEnabled) throw new BusinessException("DELIVERY_POLICY_DISABLED");

        if (type == FulfillmentType.CONSUMA_NETWORK_DELIVERY) {
            if (deliveryMode != DeliveryMode.CONSUMA_NETWORK && deliveryMode != DeliveryMode.HYBRID) {
                throw new BusinessException("DELIVERY_POLICY_DISABLED");
            }
        }
        if (type == FulfillmentType.TENANT_DELIVERY) {
            if (deliveryMode != DeliveryMode.TENANT_OWN_DELIVERY && deliveryMode != DeliveryMode.HYBRID) {
                throw new BusinessException("DELIVERY_POLICY_DISABLED");
            }
        }

        List<ItemPedido> items = itemPedidoRepository.findByPedidoId(pedidoId);
        for (ItemPedido it : items) {
            if (it == null || it.getProduto() == null || it.getProduto().getId() == null) continue;
            var ppol = productDeliveryPolicyService.getOrNull(tenantId, it.getProduto().getId());
            if (ppol != null && !ppol.isDeliveryEligible()) {
                throw new BusinessException("PRODUCT_NOT_DELIVERY_ELIGIBLE");
            }
        }
    }

    private static OrderFulfillmentResponse map(OrderFulfillment f) {
        return new OrderFulfillmentResponse(
                f.getId(),
                f.getPedido() != null ? f.getPedido().getId() : null,
                f.getFulfillmentType(),
                f.getStatus(),
                f.getCustomerName(),
                f.getCustomerPhoneMasked(),
                f.getDeliveryAddressText(),
                f.getDeliveryLatitude(),
                f.getDeliveryLongitude(),
                f.getDeliveryNotes(),
                f.getDeliveryRequestedAt(),
                f.getPickupReadyAt(),
                f.getCompletedAt(),
                f.getCancelledAt(),
                f.getCancellationReason()
        );
    }
}

