package com.restaurante.financeiro.snapshot.evidence.service;

import com.restaurante.financeiro.snapshot.evidence.EvidenceBundleProperties;
import com.restaurante.financeiro.snapshot.evidence.entity.TurnoEvidenceBundle;
import com.restaurante.financeiro.snapshot.evidence.entity.TurnoEvidenceBundleAccessLog;
import com.restaurante.financeiro.snapshot.evidence.repository.TurnoEvidenceBundleAccessLogRepository;
import com.restaurante.financeiro.snapshot.evidence.repository.TurnoEvidenceBundleRepository;
import com.restaurante.model.enums.EvidenceBundleAccessType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EvidenceBundleRetentionService {

    private final TurnoEvidenceBundleRepository bundleRepository;
    private final TurnoEvidenceBundleAccessLogRepository accessLogRepository;
    private final OperationalEventLogService operationalEventLogService;
    private final EvidenceBundleProperties props;

    @Transactional
    public RetentionRunResult runOnce(String triggeredBy) {
        int batchSize = Math.max(1, props.getRetentionJobBatchSize());
        List<Long> ids = bundleRepository.lockExpiredIdsForRetention(batchSize);
        if (ids.isEmpty()) {
            return new RetentionRunResult(0, 0);
        }

        int updated = bundleRepository.markRetentionExpired(ids, triggeredBy != null ? triggeredBy : "retention_job");

        // Registrar access log + eventos (sanitizados). Evitar tocar no bundle.
        List<TurnoEvidenceBundle> bundles = bundleRepository.findAllById(ids);
        LocalDateTime now = LocalDateTime.now();
        for (TurnoEvidenceBundle b : bundles) {
            TurnoEvidenceBundleAccessLog log = new TurnoEvidenceBundleAccessLog();
            log.setTenant(b.getTenant());
            log.setBundle(b);
            log.setTurno(b.getTurno());
            log.setAccessedAt(now);
            log.setAccessedByUser(null);
            log.setActorType("SYSTEM");
            log.setAccessType(EvidenceBundleAccessType.RETENTION_EXPIRED);
            log.setSourceIp(null);
            log.setUserAgent(null);
            log.setVerificationResult("RETENTION_EXPIRED");
            log.setMetadataJson(null);
            accessLogRepository.save(log);

            if (b.getTurno() != null) {
                operationalEventLogService.logTurnoEvent(
                        OperationalEventType.EVIDENCE_BUNDLE_RETENTION_EXPIRED,
                        b.getTurno(),
                        OperationalOrigem.SYSTEM,
                        "Evidence bundle marcado como RETENTION_EXPIRED (retenção lógica)",
                        Map.of(
                                "bundleId", b.getId(),
                                "sequenceNumber", b.getSequenceNumber(),
                                "retentionUntil", b.getRetentionUntil()
                        ),
                        null,
                        null
                );
            }
        }

        return new RetentionRunResult(ids.size(), updated);
    }

    public record RetentionRunResult(int totalProcessados, int totalMarcadosExpirados) {}
}

