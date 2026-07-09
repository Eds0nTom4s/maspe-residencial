package com.restaurante.financeiro.paymentmethod.repository;

import com.restaurante.financeiro.paymentmethod.entity.PaymentMethodPolicyRollout;
import com.restaurante.model.enums.PaymentMethodPolicyRolloutExecutionMode;
import com.restaurante.model.enums.PaymentMethodPolicyRolloutStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentMethodPolicyRolloutRepository extends JpaRepository<PaymentMethodPolicyRollout, Long> {

    Optional<PaymentMethodPolicyRollout> findByIdAndTenant_Id(Long id, Long tenantId);

    Optional<PaymentMethodPolicyRollout> findByTenant_IdAndIdempotencyKey(Long tenantId, String idempotencyKey);

    @Modifying
    @Query("""
            update PaymentMethodPolicyRollout r
               set r.lockedAt = :now,
                   r.lockedBy = :lockedBy
             where r.id = :rolloutId
               and r.tenant.id = :tenantId
               and (r.lockedAt is null or r.lockedAt < :lockExpiredAt)
            """)
    int tryLock(@Param("tenantId") Long tenantId,
                @Param("rolloutId") Long rolloutId,
                @Param("now") Instant now,
                @Param("lockExpiredAt") Instant lockExpiredAt,
                @Param("lockedBy") String lockedBy);

    @Query("""
            select r
            from PaymentMethodPolicyRollout r
            where r.executionMode = :mode
              and r.status in :statuses
              and (r.nextRetryAt is null or r.nextRetryAt <= :now)
              and (r.lockedAt is null or r.lockedAt < :lockExpiredAt)
            order by r.id asc
            """)
    List<PaymentMethodPolicyRollout> findNextEligible(
            @Param("mode") PaymentMethodPolicyRolloutExecutionMode mode,
            @Param("statuses") java.util.Collection<PaymentMethodPolicyRolloutStatus> statuses,
            @Param("now") Instant now,
            @Param("lockExpiredAt") Instant lockExpiredAt,
            Pageable pageable
    );
}
