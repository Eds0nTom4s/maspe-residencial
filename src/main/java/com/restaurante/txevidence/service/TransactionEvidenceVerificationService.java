package com.restaurante.txevidence.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.TransactionEvidenceEvent;
import com.restaurante.model.entity.TransactionEvidenceVerificationIssue;
import com.restaurante.model.entity.TransactionEvidenceVerificationRun;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.TransactionEvidenceVerificationIssueType;
import com.restaurante.model.enums.TransactionEvidenceVerificationRunStatus;
import com.restaurante.model.enums.TransactionEvidenceVerificationStatus;
import com.restaurante.repository.TenantRepository;
import com.restaurante.txevidence.hash.TransactionEvidenceHashService;
import com.restaurante.txevidence.repository.TransactionEvidenceEventRepository;
import com.restaurante.txevidence.repository.TransactionEvidenceVerificationIssueRepository;
import com.restaurante.txevidence.repository.TransactionEvidenceVerificationRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionEvidenceVerificationService {

    private final TenantRepository tenantRepository;
    private final TransactionEvidenceEventRepository eventRepository;
    private final TransactionEvidenceVerificationRunRepository runRepository;
    private final TransactionEvidenceVerificationIssueRepository issueRepository;
    private final TransactionEvidenceHashService hashService;

    @Transactional
    public TransactionEvidenceVerificationRun verifyTenantLedger(Long tenantId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        if (tenantId == null) throw new BusinessException("TRANSACTION_EVIDENCE_FORBIDDEN");
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
            throw new BusinessException("TRANSACTION_EVIDENCE_VERIFICATION_FAILED");
        }

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND"));

        TransactionEvidenceVerificationRun run = new TransactionEvidenceVerificationRun();
        run.setTenant(tenant);
        run.setPeriodStart(periodStart);
        run.setPeriodEnd(periodEnd);
        run.setStatus(TransactionEvidenceVerificationRunStatus.RUNNING);
        run = runRepository.save(run);

        List<TransactionEvidenceEvent> events = eventRepository.findByTenantIdAndOccurredAtBetweenOrderByLedgerSequenceAsc(
                tenantId, periodStart, periodEnd
        );

        int checked = 0;
        int invalid = 0;
        int brokenChain = 0;
        int seqGap = 0;
        List<TransactionEvidenceVerificationIssue> issues = new ArrayList<>();

        Long expectedSeq = null;
        TransactionEvidenceEvent prev = null;

        for (TransactionEvidenceEvent ev : events) {
            checked++;

            if (expectedSeq == null) expectedSeq = ev.getLedgerSequence();

            if (ev.getLedgerSequence() == null || !ev.getLedgerSequence().equals(expectedSeq)) {
                seqGap++;
                issues.add(issue(run, tenant, ev, ev.getLedgerSequence(), TransactionEvidenceVerificationIssueType.SEQUENCE_GAP,
                        "Sequence gap detected. expected=" + expectedSeq + ", actual=" + ev.getLedgerSequence()));
                expectedSeq = ev.getLedgerSequence() != null ? ev.getLedgerSequence() : expectedSeq;
            }

            TransactionEvidenceEvent prevActual = prev;
            if (prevActual == null && ev.getLedgerSequence() != null && ev.getLedgerSequence() > 1) {
                prevActual = eventRepository.findByTenantIdAndLedgerSequence(tenantId, ev.getLedgerSequence() - 1).orElse(null);
            }

            String expectedPrevHash = prevActual != null ? prevActual.getEventHash() : TransactionEvidenceLedgerService.GENESIS_HASH;
            if (ev.getLedgerSequence() != null && ev.getLedgerSequence() == 1L) {
                expectedPrevHash = TransactionEvidenceLedgerService.GENESIS_HASH;
            }
            if (expectedPrevHash != null && ev.getPreviousEventHash() != null && !expectedPrevHash.equals(ev.getPreviousEventHash())) {
                brokenChain++;
                issues.add(issue(run, tenant, ev, ev.getLedgerSequence(), TransactionEvidenceVerificationIssueType.BROKEN_CHAIN,
                        "Previous hash mismatch. expected=" + expectedPrevHash + ", actual=" + ev.getPreviousEventHash()));
            }

            boolean thisValid = true;

            String canonicalJson = ev.getCanonicalPayloadJson();
            String computedPayloadHash = hashService.canonicalPayloadHash(canonicalJson);
            if (!computedPayloadHash.equalsIgnoreCase(ev.getCanonicalPayloadHash())) {
                thisValid = false;
                issues.add(issue(run, tenant, ev, ev.getLedgerSequence(), TransactionEvidenceVerificationIssueType.HASH_MISMATCH,
                        "Canonical payload hash mismatch."));
            }

            String occurredAtUtc = ev.getOccurredAt() != null ? ev.getOccurredAt().atOffset(ZoneOffset.UTC).toString() : null;
            String computedEventHash = hashService.eventHash(
                    tenantId,
                    ev.getLedgerSequence(),
                    ev.getEventType(),
                    ev.getSourceModule() != null ? ev.getSourceModule().name() : null,
                    ev.getSourceEntityType(),
                    ev.getSourceEntityId(),
                    occurredAtUtc,
                    computedPayloadHash,
                    ev.getPreviousEventHash(),
                    ev.getKeyVersion()
            );
            if (!computedEventHash.equalsIgnoreCase(ev.getEventHash())) {
                thisValid = false;
                issues.add(issue(run, tenant, ev, ev.getLedgerSequence(), TransactionEvidenceVerificationIssueType.HASH_MISMATCH,
                        "Event hash mismatch."));
            }

            boolean sigOk = hashService.verifyEventSignature(computedEventHash, ev.getHmacSignature(), ev.getKeyVersion(), ev.getAlgorithm());
            if (!sigOk) {
                thisValid = false;
                issues.add(issue(run, tenant, ev, ev.getLedgerSequence(), TransactionEvidenceVerificationIssueType.SIGNATURE_MISMATCH,
                        "Signature mismatch or key unavailable."));
            }

            if (!thisValid) invalid++;

            ev.setVerificationStatus(thisValid ? TransactionEvidenceVerificationStatus.VALID : TransactionEvidenceVerificationStatus.INVALID_HASH);
            // Note: do not persist per-event status changes in MVP to keep append-only semantics.

            prev = ev;
            expectedSeq = expectedSeq + 1;
        }

        issueRepository.saveAll(issues);

        run.setCheckedEventsCount(checked);
        run.setInvalidEventsCount(invalid);
        run.setBrokenChainCount(brokenChain);
        run.setSequenceGapCount(seqGap);
        run.setFinishedAt(LocalDateTime.now());
        run.setStatus((invalid == 0 && brokenChain == 0 && seqGap == 0) ? TransactionEvidenceVerificationRunStatus.VALID : TransactionEvidenceVerificationRunStatus.INVALID);
        return runRepository.save(run);
    }

    private static TransactionEvidenceVerificationIssue issue(TransactionEvidenceVerificationRun run,
                                                             Tenant tenant,
                                                             TransactionEvidenceEvent ev,
                                                             Long ledgerSequence,
                                                             TransactionEvidenceVerificationIssueType type,
                                                             String description) {
        TransactionEvidenceVerificationIssue i = new TransactionEvidenceVerificationIssue();
        i.setVerificationRun(run);
        i.setTenant(tenant);
        i.setEvent(ev);
        i.setLedgerSequence(ledgerSequence);
        i.setIssueType(type);
        i.setDescription(description);
        i.setDetectedAt(LocalDateTime.now());
        return i;
    }
}

