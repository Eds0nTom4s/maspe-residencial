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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DeliveryJobService {

    private final TenantRepository tenantRepository;
    private final PedidoRepository pedidoRepository;
    private final OrderFulfillmentRepository orderFulfillmentRepository;
    private final DeliveryJobRepository deliveryJobRepository;
    private final DeliveryCourierInviteRepository inviteRepository;
    private final CourierProfileRepository courierProfileRepository;
    private final DeliveryCourierMatchingService matchingService;
    private final TenantDeliveryPolicyService tenantDeliveryPolicyService;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public DeliveryJob createJobForFulfillment(Long tenantId, Long pedidoId) {
        if (tenantId == null || pedidoId == null) {
            throw new BusinessException("DELIVERY_JOB_NOT_FOUND");
        }

        // Idempotency check
        Optional<DeliveryJob> existing = deliveryJobRepository.findByTenantIdAndPedido_Id(tenantId, pedidoId);
        if (existing.isPresent()) {
            return existing.get();
        }

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND"));
        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow(() -> new BusinessException("PEDIDO_NOT_FOUND"));
        OrderFulfillment fulfillment = orderFulfillmentRepository.findByTenantIdAndPedido_Id(tenantId, pedidoId)
                .orElseThrow(() -> new BusinessException("FULFILLMENT_NOT_FOUND"));

        if (fulfillment.getFulfillmentType() != FulfillmentType.CONSUMA_NETWORK_DELIVERY) {
            throw new BusinessException("DELIVERY_JOB_INVALID_STATE");
        }

        var policy = tenantDeliveryPolicyService.getOrCreateDefault(tenantId);
        if (policy.getStatus() != TenantDeliveryPolicyStatus.ACTIVE) {
            throw new BusinessException("DELIVERY_POLICY_DISABLED");
        }

        DeliveryJob job = new DeliveryJob();
        job.setTenant(tenant);
        job.setPedido(pedido);
        job.setOrderFulfillment(fulfillment);
        job.setStatus(DeliveryJobStatus.CREATED);

        // Geolocation setup - Luanda defaults since coordinates are not bound to restaurant model
        job.setPickupAddressText(tenant.getNome() != null ? tenant.getNome() : "Estabelecimento Principal");
        job.setPickupLatitude(new BigDecimal("-8.836800"));
        job.setPickupLongitude(new BigDecimal("13.234300"));

        job.setDeliveryAddressText(fulfillment.getDeliveryAddressText());
        job.setDeliveryLatitude(fulfillment.getDeliveryLatitude());
        job.setDeliveryLongitude(fulfillment.getDeliveryLongitude());

        job.setEstimatedDistanceKm(BigDecimal.valueOf(2.5)); // MVP informative distance
        job.setEstimatedDeliveryFee(BigDecimal.valueOf(1500.0)); // MVP informative fee
        job.setDeliveryFeeCurrency("AOA");
        job.setDeliveryFeePaymentStatus(DeliveryFeePaymentStatus.NOT_CHARGED);
        job.setRequestedAt(LocalDateTime.now());

        job = deliveryJobRepository.save(job);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.DELIVERY_JOB_CREATED,
                OperationalEntityType.DELIVERY_JOB,
                job.getId(),
                OperationalOrigem.SYSTEM,
                "DeliveryJob criado com sucesso",
                Map.of("tenantId", tenantId, "pedidoId", pedidoId, "jobId", job.getId()),
                null,
                null
        );

        // Transition immediately to searching
        startCourierSearch(tenantId, job.getId());

        return job;
    }

    @Transactional
    public void startCourierSearch(Long tenantId, Long jobId) {
        DeliveryJob job = deliveryJobRepository.findByTenantIdAndId(tenantId, jobId)
                .orElseThrow(() -> new BusinessException("DELIVERY_JOB_NOT_FOUND"));

        if (job.getStatus() != DeliveryJobStatus.CREATED && job.getStatus() != DeliveryJobStatus.SEARCHING_COURIER && job.getStatus() != DeliveryJobStatus.COURIER_REJECTED) {
            throw new BusinessException("DELIVERY_JOB_INVALID_STATE");
        }

        job.setStatus(DeliveryJobStatus.SEARCHING_COURIER);
        job = deliveryJobRepository.save(job);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.DELIVERY_COURIER_SEARCH_STARTED,
                OperationalEntityType.DELIVERY_JOB,
                job.getId(),
                OperationalOrigem.SYSTEM,
                "Busca por entregadores iniciada",
                Map.of("tenantId", tenantId, "jobId", job.getId()),
                null,
                null
        );

        var policy = tenantDeliveryPolicyService.getOrCreateDefault(tenantId);
        List<CourierProfile> candidates = matchingService.findMatchingCouriers(
                tenantId,
                job.getPedido().getId(),
                job.getPickupLatitude(),
                job.getPickupLongitude(),
                policy.getMaxDeliveryDistanceKm()
        );

        if (candidates.isEmpty()) {
            job.setStatus(DeliveryJobStatus.FAILED_NO_COURIER);
            deliveryJobRepository.save(job);

            OrderFulfillment fulfillment = job.getOrderFulfillment();
            fulfillment.setStatus(OrderFulfillmentStatus.FAILED);
            orderFulfillmentRepository.save(fulfillment);

            operationalEventLogService.logGenericForTenant(
                    tenantId,
                    OperationalEventType.DELIVERY_FAILED_NO_COURIER,
                    OperationalEntityType.DELIVERY_JOB,
                    job.getId(),
                    OperationalOrigem.SYSTEM,
                    "Falha ao encontrar entregador disponível",
                    Map.of("tenantId", tenantId, "jobId", job.getId()),
                    null,
                    null
            );
            return;
        }

        // Invite the closest candidate
        CourierProfile bestCandidate = candidates.get(0);
        DeliveryCourierInvite invite = new DeliveryCourierInvite();
        invite.setDeliveryJob(job);
        invite.setTenant(job.getTenant());
        invite.setCourier(bestCandidate);
        invite.setStatus(DeliveryCourierInviteStatus.PENDING);
        invite.setDistanceToPickupKm(BigDecimal.valueOf(1.2)); // Informative distance to pickup
        invite.setInvitedAt(LocalDateTime.now());
        invite.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        inviteRepository.save(invite);

        job.setStatus(DeliveryJobStatus.COURIER_INVITED);
        deliveryJobRepository.save(job);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.DELIVERY_COURIER_INVITED,
                OperationalEntityType.DELIVERY_JOB,
                job.getId(),
                OperationalOrigem.SYSTEM,
                "Entregador convidado",
                Map.of("tenantId", tenantId, "jobId", job.getId(), "courierId", bestCandidate.getId()),
                null,
                null
        );
    }

    @Transactional
    public void acceptInvite(Long tenantId, Long inviteId, Long courierUserId) {
        DeliveryCourierInvite invite = inviteRepository.findByTenantIdAndId(tenantId, inviteId)
                .orElseThrow(() -> new BusinessException("DELIVERY_INVITE_NOT_FOUND"));

        if (invite.getStatus() != DeliveryCourierInviteStatus.PENDING) {
            if (invite.getStatus() == DeliveryCourierInviteStatus.ACCEPTED) {
                throw new BusinessException("DELIVERY_INVITE_ALREADY_ACCEPTED");
            }
            throw new BusinessException("DELIVERY_INVITE_EXPIRED");
        }

        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            invite.setStatus(DeliveryCourierInviteStatus.EXPIRED);
            inviteRepository.save(invite);
            throw new BusinessException("DELIVERY_INVITE_EXPIRED");
        }

        CourierProfile courier = invite.getCourier();
        if (courier.getCourierUser() == null || !courierUserId.equals(courier.getCourierUser().getId())) {
            throw new BusinessException("DELIVERY_FORBIDDEN");
        }

        if (courier.getStatus() != CourierStatus.ACTIVE) {
            throw new BusinessException("DELIVERY_COURIER_NOT_AVAILABLE");
        }
        if (courier.getVerificationStatus() != CourierVerificationStatus.VERIFIED) {
            throw new BusinessException("DELIVERY_COURIER_NOT_VERIFIED");
        }
        if (courier.getActiveDeliveryJobId() != null) {
            throw new BusinessException("DELIVERY_COURIER_ALREADY_BUSY");
        }

        DeliveryJob job = invite.getDeliveryJob();
        if (job.getStatus() != DeliveryJobStatus.COURIER_INVITED && job.getStatus() != DeliveryJobStatus.SEARCHING_COURIER) {
            throw new BusinessException("DELIVERY_JOB_INVALID_STATE");
        }

        // Double check assignment safety
        if (job.getCourier() != null) {
            throw new BusinessException("DELIVERY_INVITE_INVALID_STATE");
        }

        // Accept invite
        invite.setStatus(DeliveryCourierInviteStatus.ACCEPTED);
        invite.setRespondedAt(LocalDateTime.now());
        inviteRepository.save(invite);

        // Cancel all other pending invites for this job
        List<DeliveryCourierInvite> otherInvites = inviteRepository.findByTenantIdAndDeliveryJob_IdOrderByInvitedAtAsc(tenantId, job.getId());
        for (DeliveryCourierInvite other : otherInvites) {
            if (other.getId().equals(invite.getId())) continue;
            if (other.getStatus() == DeliveryCourierInviteStatus.PENDING) {
                other.setStatus(DeliveryCourierInviteStatus.CANCELLED);
                inviteRepository.save(other);
            }
        }

        // Bind courier to delivery job
        job.setCourier(courier);
        job.setStatus(DeliveryJobStatus.COURIER_ACCEPTED);
        job.setAssignedAt(LocalDateTime.now());
        job.setAcceptedAt(LocalDateTime.now());
        deliveryJobRepository.save(job);

        // Update fulfillment status
        OrderFulfillment fulfillment = job.getOrderFulfillment();
        fulfillment.setStatus(OrderFulfillmentStatus.COURIER_ASSIGNED);
        orderFulfillmentRepository.save(fulfillment);

        // Update courier availability
        courier.setCurrentAvailability(CourierAvailability.ONLINE_BUSY);
        courier.setActiveDeliveryJobId(job.getId());
        courierProfileRepository.save(courier);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.DELIVERY_COURIER_INVITE_ACCEPTED,
                OperationalEntityType.DELIVERY_JOB,
                job.getId(),
                OperationalOrigem.SYSTEM,
                "Convite aceito pelo entregador",
                Map.of("tenantId", tenantId, "jobId", job.getId(), "courierId", courier.getId()),
                null,
                null
        );

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.DELIVERY_JOB_ASSIGNED,
                OperationalEntityType.DELIVERY_JOB,
                job.getId(),
                OperationalOrigem.SYSTEM,
                "Entregador atribuído ao job",
                Map.of("tenantId", tenantId, "jobId", job.getId(), "courierId", courier.getId()),
                null,
                null
        );
    }

    @Transactional
    public void rejectInvite(Long tenantId, Long inviteId, Long courierUserId, String reason) {
        DeliveryCourierInvite invite = inviteRepository.findByTenantIdAndId(tenantId, inviteId)
                .orElseThrow(() -> new BusinessException("DELIVERY_INVITE_NOT_FOUND"));

        if (invite.getStatus() != DeliveryCourierInviteStatus.PENDING) {
            throw new BusinessException("DELIVERY_INVITE_INVALID_STATE");
        }

        CourierProfile courier = invite.getCourier();
        if (courier.getCourierUser() == null || !courierUserId.equals(courier.getCourierUser().getId())) {
            throw new BusinessException("DELIVERY_FORBIDDEN");
        }

        invite.setStatus(DeliveryCourierInviteStatus.REJECTED);
        invite.setRespondedAt(LocalDateTime.now());
        invite.setRejectionReason(reason);
        inviteRepository.save(invite);

        DeliveryJob job = invite.getDeliveryJob();
        job.setStatus(DeliveryJobStatus.COURIER_REJECTED);
        deliveryJobRepository.save(job);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.DELIVERY_COURIER_INVITE_REJECTED,
                OperationalEntityType.DELIVERY_JOB,
                job.getId(),
                OperationalOrigem.SYSTEM,
                "Convite rejeitado pelo entregador",
                Map.of("tenantId", tenantId, "jobId", job.getId(), "courierId", courier.getId(), "reason", reason != null ? reason : ""),
                null,
                null
        );

        // Attempt to find another courier
        startCourierSearch(tenantId, job.getId());
    }

    @Transactional
    public void markReadyForPickup(Long tenantId, Long jobId) {
        DeliveryJob job = deliveryJobRepository.findByTenantIdAndId(tenantId, jobId)
                .orElseThrow(() -> new BusinessException("DELIVERY_JOB_NOT_FOUND"));

        if (job.getStatus() == DeliveryJobStatus.CANCELLED_BY_CUSTOMER ||
            job.getStatus() == DeliveryJobStatus.CANCELLED_BY_TENANT ||
            job.getStatus() == DeliveryJobStatus.DELIVERED) {
            throw new BusinessException("DELIVERY_JOB_INVALID_STATE");
        }

        job.setStatus(DeliveryJobStatus.PICKUP_READY);
        deliveryJobRepository.save(job);

        OrderFulfillment fulfillment = job.getOrderFulfillment();
        fulfillment.setStatus(OrderFulfillmentStatus.READY_FOR_PICKUP);
        fulfillment.setPickupReadyAt(LocalDateTime.now());
        orderFulfillmentRepository.save(fulfillment);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.DELIVERY_PICKUP_READY,
                OperationalEntityType.DELIVERY_JOB,
                job.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Pedido pronto para retirada",
                Map.of("tenantId", tenantId, "jobId", job.getId()),
                null,
                null
        );
    }

    @Transactional
    public void pickedUp(Long tenantId, Long jobId, Long courierUserId) {
        DeliveryJob job = deliveryJobRepository.findByTenantIdAndId(tenantId, jobId)
                .orElseThrow(() -> new BusinessException("DELIVERY_JOB_NOT_FOUND"));

        if (job.getCourier() == null || job.getCourier().getCourierUser() == null ||
            !courierUserId.equals(job.getCourier().getCourierUser().getId())) {
            throw new BusinessException("DELIVERY_FORBIDDEN");
        }

        if (job.getStatus() != DeliveryJobStatus.COURIER_ACCEPTED && job.getStatus() != DeliveryJobStatus.PICKUP_READY) {
            throw new BusinessException("DELIVERY_JOB_INVALID_STATE");
        }

        job.setStatus(DeliveryJobStatus.PICKED_UP);
        job.setPickedUpAt(LocalDateTime.now());
        deliveryJobRepository.save(job);

        OrderFulfillment fulfillment = job.getOrderFulfillment();
        fulfillment.setStatus(OrderFulfillmentStatus.PICKED_UP);
        orderFulfillmentRepository.save(fulfillment);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.DELIVERY_PICKED_UP,
                OperationalEntityType.DELIVERY_JOB,
                job.getId(),
                OperationalOrigem.SYSTEM,
                "Pedido coletado pelo entregador",
                Map.of("tenantId", tenantId, "jobId", job.getId(), "courierId", job.getCourier().getId()),
                null,
                null
        );
    }

    @Transactional
    public void inTransit(Long tenantId, Long jobId, Long courierUserId) {
        DeliveryJob job = deliveryJobRepository.findByTenantIdAndId(tenantId, jobId)
                .orElseThrow(() -> new BusinessException("DELIVERY_JOB_NOT_FOUND"));

        if (job.getCourier() == null || job.getCourier().getCourierUser() == null ||
            !courierUserId.equals(job.getCourier().getCourierUser().getId())) {
            throw new BusinessException("DELIVERY_FORBIDDEN");
        }

        if (job.getStatus() != DeliveryJobStatus.PICKED_UP) {
            throw new BusinessException("DELIVERY_JOB_INVALID_STATE");
        }

        job.setStatus(DeliveryJobStatus.IN_TRANSIT);
        deliveryJobRepository.save(job);

        OrderFulfillment fulfillment = job.getOrderFulfillment();
        fulfillment.setStatus(OrderFulfillmentStatus.IN_TRANSIT);
        orderFulfillmentRepository.save(fulfillment);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.DELIVERY_IN_TRANSIT,
                OperationalEntityType.DELIVERY_JOB,
                job.getId(),
                OperationalOrigem.SYSTEM,
                "Entrega em trânsito",
                Map.of("tenantId", tenantId, "jobId", job.getId(), "courierId", job.getCourier().getId()),
                null,
                null
        );
    }

    @Transactional
    public void delivered(Long tenantId, Long jobId, Long courierUserId) {
        DeliveryJob job = deliveryJobRepository.findByTenantIdAndId(tenantId, jobId)
                .orElseThrow(() -> new BusinessException("DELIVERY_JOB_NOT_FOUND"));

        if (job.getCourier() == null || job.getCourier().getCourierUser() == null ||
            !courierUserId.equals(job.getCourier().getCourierUser().getId())) {
            throw new BusinessException("DELIVERY_FORBIDDEN");
        }

        if (job.getStatus() != DeliveryJobStatus.IN_TRANSIT) {
            throw new BusinessException("DELIVERY_JOB_INVALID_STATE");
        }

        job.setStatus(DeliveryJobStatus.DELIVERED);
        job.setDeliveredAt(LocalDateTime.now());
        job.setFinalDeliveryFee(job.getEstimatedDeliveryFee());
        job.setDeliveryFeePaymentStatus(DeliveryFeePaymentStatus.PAID);
        deliveryJobRepository.save(job);

        OrderFulfillment fulfillment = job.getOrderFulfillment();
        fulfillment.setStatus(OrderFulfillmentStatus.DELIVERED);
        fulfillment.setCompletedAt(LocalDateTime.now());
        orderFulfillmentRepository.save(fulfillment);

        // Free courier profile
        CourierProfile courier = job.getCourier();
        courier.setCurrentAvailability(CourierAvailability.ONLINE_AVAILABLE);
        courier.setActiveDeliveryJobId(null);
        courierProfileRepository.save(courier);

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.DELIVERY_DELIVERED,
                OperationalEntityType.DELIVERY_JOB,
                job.getId(),
                OperationalOrigem.SYSTEM,
                "Entrega concluída com sucesso",
                Map.of("tenantId", tenantId, "jobId", job.getId(), "courierId", courier.getId()),
                null,
                null
        );
    }

    @Transactional
    public void reportIssue(Long tenantId, Long jobId, Long courierUserId, String reason) {
        DeliveryJob job = deliveryJobRepository.findByTenantIdAndId(tenantId, jobId)
                .orElseThrow(() -> new BusinessException("DELIVERY_JOB_NOT_FOUND"));

        CourierProfile courier = job.getCourier();
        if (courier != null && (courier.getCourierUser() == null || !courierUserId.equals(courier.getCourierUser().getId()))) {
            throw new BusinessException("DELIVERY_FORBIDDEN");
        }

        job.setStatus(DeliveryJobStatus.FAILED_DELIVERY_PROBLEM);
        job.setFailedAt(LocalDateTime.now());
        job.setFailureReason(reason);
        deliveryJobRepository.save(job);

        OrderFulfillment fulfillment = job.getOrderFulfillment();
        fulfillment.setStatus(OrderFulfillmentStatus.FAILED);
        orderFulfillmentRepository.save(fulfillment);

        if (courier != null) {
            courier.setCurrentAvailability(CourierAvailability.ONLINE_AVAILABLE);
            courier.setActiveDeliveryJobId(null);
            courierProfileRepository.save(courier);
        }

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.DELIVERY_ISSUE_REPORTED,
                OperationalEntityType.DELIVERY_JOB,
                job.getId(),
                OperationalOrigem.SYSTEM,
                "Incidente operacional reportado na entrega",
                Map.of("tenantId", tenantId, "jobId", jobId, "reason", reason != null ? reason : ""),
                null,
                null
        );
    }

    @Transactional
    public void cancelByCustomer(Long tenantId, Long pedidoId, String reason) {
        DeliveryJob job = deliveryJobRepository.findByTenantIdAndPedido_Id(tenantId, pedidoId)
                .orElseThrow(() -> new BusinessException("DELIVERY_JOB_NOT_FOUND"));

        var policy = tenantDeliveryPolicyService.getOrCreateDefault(tenantId);
        DeliveryCancelAllowedUntilStatus limit = policy.getCancelAllowedUntilStatus();

        if (limit == DeliveryCancelAllowedUntilStatus.NEVER_AFTER_PAYMENT) {
            throw new BusinessException("DELIVERY_CANCEL_NOT_ALLOWED");
        }

        if (limit == DeliveryCancelAllowedUntilStatus.BEFORE_COURIER_ACCEPTED) {
            if (job.getStatus() != DeliveryJobStatus.CREATED &&
                job.getStatus() != DeliveryJobStatus.SEARCHING_COURIER &&
                job.getStatus() != DeliveryJobStatus.COURIER_INVITED &&
                job.getStatus() != DeliveryJobStatus.COURIER_REJECTED) {
                throw new BusinessException("DELIVERY_CANCEL_NOT_ALLOWED");
            }
        }

        if (limit == DeliveryCancelAllowedUntilStatus.BEFORE_PICKUP) {
            if (job.getStatus() == java.util.Arrays.asList(DeliveryJobStatus.PICKED_UP, DeliveryJobStatus.IN_TRANSIT, DeliveryJobStatus.DELIVERED).get(0)) {
                throw new BusinessException("DELIVERY_CANCEL_NOT_ALLOWED");
            }
            if (job.getStatus() == DeliveryJobStatus.PICKED_UP || job.getStatus() == DeliveryJobStatus.IN_TRANSIT || job.getStatus() == DeliveryJobStatus.DELIVERED) {
                throw new BusinessException("DELIVERY_CANCEL_NOT_ALLOWED");
            }
        }

        // Cancel job
        job.setStatus(DeliveryJobStatus.CANCELLED_BY_CUSTOMER);
        job.setCancelledAt(LocalDateTime.now());
        job.setCancellationReason(reason);
        deliveryJobRepository.save(job);

        // Cancel fulfillment
        OrderFulfillment f = job.getOrderFulfillment();
        f.setStatus(OrderFulfillmentStatus.CANCELLED);
        f.setCancelledAt(LocalDateTime.now());
        f.setCancellationReason(reason);
        orderFulfillmentRepository.save(f);

        // Free courier if assigned
        CourierProfile courier = job.getCourier();
        if (courier != null) {
            courier.setCurrentAvailability(CourierAvailability.ONLINE_AVAILABLE);
            courier.setActiveDeliveryJobId(null);
            courierProfileRepository.save(courier);
        }

        // Cancel all pending invites for this job
        List<DeliveryCourierInvite> invites = inviteRepository.findByTenantIdAndDeliveryJob_IdOrderByInvitedAtAsc(tenantId, job.getId());
        for (DeliveryCourierInvite invite : invites) {
            if (invite.getStatus() == DeliveryCourierInviteStatus.PENDING) {
                invite.setStatus(DeliveryCourierInviteStatus.CANCELLED);
                inviteRepository.save(invite);
            }
        }

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.DELIVERY_CANCELLED_BY_CUSTOMER,
                OperationalEntityType.DELIVERY_JOB,
                job.getId(),
                OperationalOrigem.QR_PUBLICO,
                "Entrega cancelada pelo cliente",
                Map.of("tenantId", tenantId, "jobId", job.getId(), "reason", reason != null ? reason : ""),
                null,
                null
        );
    }
}
