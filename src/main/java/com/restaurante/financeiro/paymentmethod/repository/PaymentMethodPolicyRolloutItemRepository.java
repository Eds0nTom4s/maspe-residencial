package com.restaurante.financeiro.paymentmethod.repository;

import com.restaurante.financeiro.paymentmethod.entity.PaymentMethodPolicyRolloutItem;
import com.restaurante.model.enums.PaymentMethodPolicyRolloutItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentMethodPolicyRolloutItemRepository extends JpaRepository<PaymentMethodPolicyRolloutItem, Long> {

    Optional<PaymentMethodPolicyRolloutItem> findByIdAndTenant_Id(Long id, Long tenantId);

    @Query("""
            select i
            from PaymentMethodPolicyRolloutItem i
            where i.tenant.id = :tenantId
              and i.rollout.id = :rolloutId
              and (:status is null or i.status = :status)
            order by i.id asc
            """)
    Page<PaymentMethodPolicyRolloutItem> pageByRolloutAndStatus(
            @Param("tenantId") Long tenantId,
            @Param("rolloutId") Long rolloutId,
            @Param("status") PaymentMethodPolicyRolloutItemStatus status,
            Pageable pageable
    );

    long countByTenant_IdAndRollout_Id(Long tenantId, Long rolloutId);

    long countByTenant_IdAndRollout_IdAndStatus(Long tenantId, Long rolloutId, PaymentMethodPolicyRolloutItemStatus status);
}
