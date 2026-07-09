package com.restaurante.txevidence.evidence;

import com.restaurante.financeiro.snapshot.evidence.dto.TransactionLedgerEvidenceSectionDTO;
import com.restaurante.model.entity.TransactionEvidenceEvent;
import com.restaurante.model.entity.TransactionEvidenceLedgerState;
import com.restaurante.model.entity.TransactionEvidenceVerificationRun;
import com.restaurante.model.enums.TransactionEvidenceVerificationRunStatus;
import com.restaurante.txevidence.hash.TransactionEvidenceHashService;
import com.restaurante.txevidence.repository.TransactionEvidenceEventRepository;
import com.restaurante.txevidence.repository.TransactionEvidenceLedgerStateRepository;
import com.restaurante.txevidence.repository.TransactionEvidenceVerificationRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TransactionLedgerEvidenceService {

    private final TransactionEvidenceEventRepository eventRepository;
    private final TransactionEvidenceLedgerStateRepository stateRepository;
    private final TransactionEvidenceVerificationRunRepository runRepository;
    private final TransactionEvidenceHashService hashService;

    @Transactional(readOnly = true)
    public TransactionLedgerEvidenceSectionDTO buildForTurno(Long tenantId, LocalDateTime periodStart, LocalDateTime periodEnd, LocalDateTime generatedAt) {
        if (tenantId == null) return null;
        if (periodStart == null || periodEnd == null) return null;

        TransactionLedgerEvidenceSectionDTO out = new TransactionLedgerEvidenceSectionDTO();
        out.setGeneratedAt(generatedAt != null ? generatedAt : LocalDateTime.now());
        out.setTenantId(tenantId);
        out.setPeriodStart(periodStart);
        out.setPeriodEnd(periodEnd);

        List<String> warnings = new ArrayList<>();

        List<TransactionEvidenceEvent> events = eventRepository.findByTenantIdAndOccurredAtBetweenOrderByLedgerSequenceAsc(tenantId, periodStart, periodEnd);
        out.setTotalLedgerEvents(events.size());

        if (events.isEmpty()) {
            warnings.add("TRANSACTION_LEDGER_NO_EVENTS");
        } else {
            TransactionEvidenceEvent first = events.get(0);
            TransactionEvidenceEvent last = events.get(events.size() - 1);
            out.setFirstSequence(first.getLedgerSequence());
            out.setLastSequence(last.getLedgerSequence());
            out.setFirstEventHash(first.getEventHash());
            out.setLastEventHash(last.getEventHash());

            Map<String, Integer> byType = new HashMap<>();
            Map<String, Integer> byModule = new HashMap<>();
            for (TransactionEvidenceEvent ev : events) {
                if (ev == null) continue;
                byType.merge(ev.getEventType(), 1, Integer::sum);
                byModule.merge(ev.getSourceModule() != null ? ev.getSourceModule().name() : "UNKNOWN", 1, Integer::sum);
            }
            out.setByEventType(byType);
            out.setBySourceModule(byModule);
        }

        TransactionEvidenceLedgerState st = stateRepository.findByTenantId(tenantId).orElse(null);
        if (st != null) {
            out.setLastLedgerStateHash(hashLedgerState(st));
        }

        TransactionEvidenceVerificationRun latest = runRepository.findTopByTenantIdOrderByStartedAtDesc(tenantId).orElse(null);
        if (latest == null) {
            warnings.add("TRANSACTION_LEDGER_VERIFICATION_NOT_RUN");
            out.setVerificationStatus("NOT_RUN");
        } else {
            out.setLatestVerificationRunId(latest.getId());
            out.setInvalidEventsCount(latest.getInvalidEventsCount());
            out.setBrokenChainCount(latest.getBrokenChainCount());
            out.setSequenceGapCount(latest.getSequenceGapCount());
            out.setVerificationStatus(latest.getStatus() != null ? latest.getStatus().name() : "UNKNOWN");
            if (latest.getStatus() == TransactionEvidenceVerificationRunStatus.INVALID) {
                if (latest.getInvalidEventsCount() != null && latest.getInvalidEventsCount() > 0) warnings.add("TRANSACTION_LEDGER_INVALID_EVENTS");
                if (latest.getBrokenChainCount() != null && latest.getBrokenChainCount() > 0) warnings.add("TRANSACTION_LEDGER_BROKEN_CHAIN");
                if (latest.getSequenceGapCount() != null && latest.getSequenceGapCount() > 0) warnings.add("TRANSACTION_LEDGER_SEQUENCE_GAP");
            }
        }

        out.setWarnings(warnings);
        return out;
    }

    private String hashLedgerState(TransactionEvidenceLedgerState st) {
        String canonical = "tenantId=" + (st.getTenant() != null ? st.getTenant().getId() : null)
                + "|lastSequence=" + st.getLastSequence()
                + "|lastEventHash=" + st.getLastEventHash()
                + "|lastEventId=" + st.getLastEventId()
                + "|lastRecordedAt=" + st.getLastRecordedAt()
                + "|status=" + (st.getStatus() != null ? st.getStatus().name() : null);
        return hashService.sha256Hex(canonical);
    }
}
