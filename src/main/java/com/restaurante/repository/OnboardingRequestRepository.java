package com.restaurante.repository;

import com.restaurante.model.entity.OnboardingRequest;
import com.restaurante.model.enums.OnboardingRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

public interface OnboardingRequestRepository extends JpaRepository<OnboardingRequest, Long> {

    java.util.Optional<OnboardingRequest> findByProvisioningOperationId(Long operationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OnboardingRequest o where o.id = :id")
    java.util.Optional<OnboardingRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select o
            from OnboardingRequest o
            left join o.businessAccount ba
            left join o.plano p
            where (:status is null or o.status = :status)
              and (:search is null or :search = ''
                   or lower(o.nomeNegocio) like lower(concat('%', :search, '%'))
                   or lower(coalesce(o.nomeSolicitante, '')) like lower(concat('%', :search, '%'))
                   or lower(coalesce(o.email, '')) like lower(concat('%', :search, '%'))
                   or lower(coalesce(ba.nome, '')) like lower(concat('%', :search, '%'))
                   or lower(coalesce(p.codigo, '')) like lower(concat('%', :search, '%')))
            order by o.id desc
            """)
    Page<OnboardingRequest> search(@Param("status") OnboardingRequestStatus status,
                                   @Param("search") String search,
                                   Pageable pageable);
}
