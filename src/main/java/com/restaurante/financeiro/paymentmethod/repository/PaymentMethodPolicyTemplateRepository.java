package com.restaurante.financeiro.paymentmethod.repository;

import com.restaurante.financeiro.paymentmethod.entity.PaymentMethodPolicyTemplate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentMethodPolicyTemplateRepository extends JpaRepository<PaymentMethodPolicyTemplate, Long> {

    boolean existsByTenant_Id(Long tenantId);

    Optional<PaymentMethodPolicyTemplate> findByIdAndTenant_Id(Long id, Long tenantId);

    Optional<PaymentMethodPolicyTemplate> findByTenant_IdAndCode(Long tenantId, String code);

    @EntityGraph(attributePaths = "items")
    Optional<PaymentMethodPolicyTemplate> findWithItemsByIdAndTenant_Id(Long id, Long tenantId);

    List<PaymentMethodPolicyTemplate> findByTenant_IdOrderByIdDesc(Long tenantId);
}

