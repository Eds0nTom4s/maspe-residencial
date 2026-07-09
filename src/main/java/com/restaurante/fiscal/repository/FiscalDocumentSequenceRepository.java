package com.restaurante.fiscal.repository;

import com.restaurante.model.entity.FiscalDocumentSequence;
import com.restaurante.model.enums.FiscalDocumentSequenceStatus;
import com.restaurante.model.enums.FiscalDocumentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FiscalDocumentSequenceRepository extends JpaRepository<FiscalDocumentSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from FiscalDocumentSequence s
            where s.tenant.id = :tenantId
              and ((:unidadeId is null and s.unidadeAtendimento is null) or (s.unidadeAtendimento.id = :unidadeId))
              and s.documentType = :docType
              and s.series = :series
              and s.year = :year
            """)
    Optional<FiscalDocumentSequence> findKeyForUpdate(@Param("tenantId") Long tenantId,
                                                      @Param("unidadeId") Long unidadeId,
                                                      @Param("docType") FiscalDocumentType docType,
                                                      @Param("series") String series,
                                                      @Param("year") Integer year);

    Optional<FiscalDocumentSequence> findByTenantIdAndUnidadeAtendimentoIdAndDocumentTypeAndSeriesAndYearAndStatus(
            Long tenantId, Long unidadeId, FiscalDocumentType docType, String series, Integer year, FiscalDocumentSequenceStatus status);
}

