package com.restaurante.financeiro.paymentmethod.repository;

import com.restaurante.financeiro.paymentmethod.entity.DevicePaymentMethodPolicy;
import com.restaurante.model.enums.PaymentMethodCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DevicePaymentMethodPolicyRepository extends JpaRepository<DevicePaymentMethodPolicy, Long> {

    Optional<DevicePaymentMethodPolicy> findByTenant_IdAndDispositivoOperacional_IdAndPaymentMethodCode(Long tenantId, Long deviceId, PaymentMethodCode code);

    List<DevicePaymentMethodPolicy> findByTenant_IdAndDispositivoOperacional_Id(Long tenantId, Long deviceId);

    @Query("""
            select p
            from DevicePaymentMethodPolicy p
            where p.tenant.id = :tenantId
              and p.unidadeAtendimento.id = :unidadeId
              and p.dispositivoOperacional.id in :deviceIds
            """)
    List<DevicePaymentMethodPolicy> findByTenantAndUnidadeAndDeviceIds(
            @Param("tenantId") Long tenantId,
            @Param("unidadeId") Long unidadeId,
            @Param("deviceIds") Collection<Long> deviceIds
    );
}
