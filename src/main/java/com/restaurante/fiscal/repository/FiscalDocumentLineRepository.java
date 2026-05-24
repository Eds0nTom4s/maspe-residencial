package com.restaurante.fiscal.repository;

import com.restaurante.model.entity.FiscalDocumentLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FiscalDocumentLineRepository extends JpaRepository<FiscalDocumentLine, Long> {
    List<FiscalDocumentLine> findByTenantIdAndFiscalDocumentId(Long tenantId, Long fiscalDocumentId);
}

