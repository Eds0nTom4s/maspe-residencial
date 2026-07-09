package com.restaurante.fiscal.official.repository;

import com.restaurante.model.entity.TenantOfficialFiscalProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TenantOfficialFiscalProfileRepository extends JpaRepository<TenantOfficialFiscalProfile, Long> {
    Optional<TenantOfficialFiscalProfile> findByTenantId(Long tenantId);

    @Query("""
            select p.tenant.id
            from TenantOfficialFiscalProfile p
            where p.officialEnabled = true
            """)
    List<Long> listOfficialEnabledTenantIds();
}
