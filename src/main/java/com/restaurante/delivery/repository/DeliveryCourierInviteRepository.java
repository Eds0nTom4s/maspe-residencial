package com.restaurante.delivery.repository;

import com.restaurante.model.entity.DeliveryCourierInvite;
import com.restaurante.model.enums.DeliveryCourierInviteStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryCourierInviteRepository extends JpaRepository<DeliveryCourierInvite, Long> {
    Optional<DeliveryCourierInvite> findByTenantIdAndId(Long tenantId, Long id);
    List<DeliveryCourierInvite> findByTenantIdAndCourier_IdAndStatusOrderByInvitedAtDesc(Long tenantId, Long courierId, DeliveryCourierInviteStatus status);
    List<DeliveryCourierInvite> findByCourier_IdAndStatusOrderByInvitedAtDesc(Long courierId, DeliveryCourierInviteStatus status);
    List<DeliveryCourierInvite> findByTenantIdAndDeliveryJob_IdOrderByInvitedAtAsc(Long tenantId, Long deliveryJobId);
    Optional<DeliveryCourierInvite> findByTenantIdAndDeliveryJob_IdAndCourier_IdAndStatus(Long tenantId, Long deliveryJobId, Long courierId, DeliveryCourierInviteStatus status);
    Optional<DeliveryCourierInvite> findByIdAndCourier_Id(Long id, Long courierId);
}

