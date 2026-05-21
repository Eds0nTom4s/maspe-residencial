package com.restaurante.financeiro.paymentmethod.repository;

import com.restaurante.financeiro.paymentmethod.entity.DevicePaymentMethodPolicy;
import com.restaurante.model.enums.PaymentMethodCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DevicePaymentMethodPolicyRepository extends JpaRepository<DevicePaymentMethodPolicy, Long> {

    Optional<DevicePaymentMethodPolicy> findByTenant_IdAndDispositivoOperacional_IdAndPaymentMethodCode(Long tenantId, Long deviceId, PaymentMethodCode code);

    List<DevicePaymentMethodPolicy> findByTenant_IdAndDispositivoOperacional_Id(Long tenantId, Long deviceId);
}

