package com.restaurante.financeiro.paymentmethod.repository;

import com.restaurante.financeiro.paymentmethod.entity.TenantPaymentMethod;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantPaymentMethodRepository extends JpaRepository<TenantPaymentMethod, Long> {

    boolean existsByTenantId(Long tenantId);

    Optional<TenantPaymentMethod> findByTenantIdAndCode(Long tenantId, PaymentMethodCode code);

    List<TenantPaymentMethod> findByTenantIdOrderBySortOrderAscCodeAsc(Long tenantId);

    List<TenantPaymentMethod> findByTenantIdAndStatusOrderBySortOrderAscCodeAsc(Long tenantId, PaymentMethodStatus status);
}

