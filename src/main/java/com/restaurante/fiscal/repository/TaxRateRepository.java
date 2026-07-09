package com.restaurante.fiscal.repository;

import com.restaurante.model.entity.TaxRate;
import com.restaurante.model.enums.TaxRateStatus;
import com.restaurante.model.enums.TaxType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TaxRateRepository extends JpaRepository<TaxRate, Long> {

    @Query("""
            select r from TaxRate r
            where r.countryCode = :country
              and r.taxType = :type
              and (:status is null or r.status = :status)
            order by r.code asc
            """)
    Page<TaxRate> list(@Param("country") String country,
                       @Param("type") TaxType type,
                       @Param("status") TaxRateStatus status,
                       Pageable pageable);

    Optional<TaxRate> findByCountryCodeAndCode(String countryCode, String code);

    @Query("""
            select r from TaxRate r
            where r.id = :id
              and (:at is null
                   or ((r.effectiveFrom is null or r.effectiveFrom <= :at)
                       and (r.effectiveTo is null or r.effectiveTo >= :at)))
            """)
    Optional<TaxRate> findEffectiveById(@Param("id") Long id, @Param("at") LocalDateTime at);
}

