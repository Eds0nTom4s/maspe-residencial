package com.restaurante.fiscal.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.fiscal.config.TaxProperties;
import com.restaurante.fiscal.repository.FiscalDocumentSequenceRepository;
import com.restaurante.model.entity.FiscalDocumentSequence;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.FiscalDocumentSequenceStatus;
import com.restaurante.model.enums.FiscalDocumentType;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.security.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FiscalDocumentSequenceService {

    private final TaxProperties props;
    private final TenantGuard tenantGuard;
    private final FiscalDocumentSequenceRepository sequenceRepository;
    private final TenantRepository tenantRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;

    @Transactional
    public String nextNumber(Long tenantId, Long unidadeId, FiscalDocumentType type, String series, LocalDateTime at) {
        if (!props.isEnabled()) throw new BusinessException("Tax module desativado.");
        if (tenantId == null) throw new BusinessException("tenantId é obrigatório.");
        if (type == null) throw new BusinessException("documentType é obrigatório.");
        if (series == null || series.isBlank()) throw new BusinessException("series é obrigatória.");

        int year = (at != null ? at : LocalDateTime.now()).getYear();

        FiscalDocumentSequence seq = sequenceRepository.findKeyForUpdate(tenantId, unidadeId, type, series, year)
                .orElseGet(() -> {
                    Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new BusinessException("Tenant não encontrado."));
                    tenantGuard.assertResourceBelongsToTenant(tenant.getId());

                    UnidadeAtendimento ua = null;
                    if (unidadeId != null) {
                        ua = unidadeAtendimentoRepository.findById(unidadeId).orElseThrow(() -> new BusinessException("Unidade não encontrada."));
                    }
                    FiscalDocumentSequence n = new FiscalDocumentSequence();
                    n.setTenant(tenant);
                    n.setUnidadeAtendimento(ua);
                    n.setDocumentType(type);
                    n.setSeries(series.trim());
                    n.setYear(year);
                    n.setStatus(FiscalDocumentSequenceStatus.ACTIVE);
                    n.setCurrentNumber(0L);
                    return sequenceRepository.save(n);
                });

        if (seq.getStatus() != FiscalDocumentSequenceStatus.ACTIVE) {
            throw new BusinessException("Sequência fiscal inativa.");
        }

        long next = (seq.getCurrentNumber() != null ? seq.getCurrentNumber() : 0L) + 1L;
        seq.setCurrentNumber(next);
        sequenceRepository.save(seq);

        return format(props.getDocument().getSequencePrefix(), year, next);
    }

    private static String format(String prefix, int year, long n) {
        String p = (prefix == null || prefix.isBlank()) ? "INT" : prefix.trim();
        return p + "-" + year + "-" + String.format("%06d", n);
    }
}

