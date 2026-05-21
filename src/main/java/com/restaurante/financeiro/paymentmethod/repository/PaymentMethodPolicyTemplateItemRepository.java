package com.restaurante.financeiro.paymentmethod.repository;

import com.restaurante.financeiro.paymentmethod.entity.PaymentMethodPolicyTemplateItem;
import com.restaurante.model.enums.PaymentMethodCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentMethodPolicyTemplateItemRepository extends JpaRepository<PaymentMethodPolicyTemplateItem, Long> {

    List<PaymentMethodPolicyTemplateItem> findByTenant_IdAndTemplate_Id(Long tenantId, Long templateId);

    Optional<PaymentMethodPolicyTemplateItem> findByTenant_IdAndTemplate_IdAndPaymentMethodCode(Long tenantId, Long templateId, PaymentMethodCode code);
}

