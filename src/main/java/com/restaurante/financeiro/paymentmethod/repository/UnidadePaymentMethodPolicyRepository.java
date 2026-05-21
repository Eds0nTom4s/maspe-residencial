package com.restaurante.financeiro.paymentmethod.repository;

import com.restaurante.financeiro.paymentmethod.entity.UnidadePaymentMethodPolicy;
import com.restaurante.model.enums.PaymentMethodCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UnidadePaymentMethodPolicyRepository extends JpaRepository<UnidadePaymentMethodPolicy, Long> {

    Optional<UnidadePaymentMethodPolicy> findByTenant_IdAndUnidadeAtendimento_IdAndPaymentMethodCode(Long tenantId, Long unidadeId, PaymentMethodCode code);

    List<UnidadePaymentMethodPolicy> findByTenant_IdAndUnidadeAtendimento_Id(Long tenantId, Long unidadeId);
}

