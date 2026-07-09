package com.restaurante.txevidence.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TransactionEvidenceEvent;
import com.restaurante.model.entity.TransactionEvidenceLedgerState;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TransactionEvidenceAlgorithm;
import com.restaurante.model.enums.TransactionEvidenceEventStatus;
import com.restaurante.model.enums.TransactionEvidenceLedgerStateStatus;
import com.restaurante.model.enums.TransactionEvidenceVerificationStatus;
import com.restaurante.repository.TenantRepository;
import com.restaurante.service.operacional.OperationalEventLogService;
import com.restaurante.txevidence.canonical.TransactionEvidenceCanonicalizer;
import com.restaurante.txevidence.dto.TransactionEvidenceEventRequest;
import com.restaurante.txevidence.hash.TransactionEvidenceHashService;
import com.restaurante.txevidence.key.TransactionEvidenceKeyProvider;
import com.restaurante.txevidence.properties.TransactionEvidenceProperties;
import com.restaurante.txevidence.repository.TransactionEvidenceEventRepository;
import com.restaurante.txevidence.repository.TransactionEvidenceLedgerStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TransactionEvidenceLedgerService {

    public static final String GENESIS_HASH = "GENESIS";

    private final TransactionEvidenceProperties props;
    private final TenantRepository tenantRepository;
    private final TransactionEvidenceLedgerStateRepository stateRepository;
    private final TransactionEvidenceEventRepository eventRepository;
    private final TransactionEvidenceKeyProvider keyProvider;
    private final TransactionEvidenceCanonicalizer canonicalizer;
    private final TransactionEvidenceHashService hashService;
    private final OperationalEventLogService operationalEventLogService;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransactionEvidenceEvent recordEvidenceEvent(TransactionEvidenceEventRequest req) {
        if (!props.isEnabled()) throw new BusinessException("TRANSACTION_EVIDENCE_LEDGER_INVALID_STATE");
        if (req == null || req.getTenantId() == null) throw new BusinessException("TRANSACTION_EVIDENCE_FORBIDDEN");
        if (req.getIdempotencyKey() == null || req.getIdempotencyKey().isBlank()) throw new BusinessException("TRANSACTION_EVIDENCE_DUPLICATE_EVENT");

        TransactionEvidenceEvent existing = eventRepository.findByTenantIdAndIdempotencyKey(req.getTenantId(), req.getIdempotencyKey()).orElse(null);
        if (existing != null) return existing;

        TransactionEvidenceLedgerState state = stateRepository.lockByTenantId(req.getTenantId()).orElse(null);
        if (state == null) {
            Tenant tenant = tenantRepository.findById(req.getTenantId()).orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND"));
            state = new TransactionEvidenceLedgerState();
            state.setTenant(tenant);
            state.setLastSequence(0L);
            state.setLastEventHash(null);
            state.setLastEventId(null);
            state.setStatus(TransactionEvidenceLedgerStateStatus.ACTIVE);
            state = stateRepository.save(state);
            // lock again to keep consistent behavior
            state = stateRepository.lockByTenantId(req.getTenantId()).orElseThrow();
        }

        if (state.getStatus() != TransactionEvidenceLedgerStateStatus.ACTIVE) throw new BusinessException("TRANSACTION_EVIDENCE_LEDGER_INVALID_STATE");

        long nextSeq = (state.getLastSequence() != null ? state.getLastSequence() : 0L) + 1;
        String prevHash = state.getLastEventHash() != null ? state.getLastEventHash() : GENESIS_HASH;

        LocalDateTime occurredAt = req.getOccurredAt() != null ? req.getOccurredAt() : LocalDateTime.now();

        Map<String, Object> payloadFields = req.getPayloadFields();
        var payload = canonicalizer.canonicalize(
                req.getTenantId(),
                req.getEventType(),
                req.getSourceModule(),
                req.getSourceEntityType(),
                req.getSourceEntityId(),
                occurredAt,
                payloadFields
        );
        String canonicalJson = payload.toCanonicalJsonString();
        String payloadHash = hashService.canonicalPayloadHash(canonicalJson);

        String keyVersion = keyProvider.activeKeyVersion();
        String occurredAtUtc = occurredAt.atOffset(ZoneOffset.UTC).toString();
        String eventHash = hashService.eventHash(
                req.getTenantId(),
                nextSeq,
                req.getEventType(),
                req.getSourceModule() != null ? req.getSourceModule().name() : null,
                req.getSourceEntityType(),
                req.getSourceEntityId(),
                occurredAtUtc,
                payloadHash,
                prevHash,
                keyVersion
        );

        var sig = hashService.signEvent(eventHash, keyVersion, TransactionEvidenceAlgorithm.SHA256_HMAC);

        TransactionEvidenceEvent ev = new TransactionEvidenceEvent();
        ev.setTenant(state.getTenant());
        ev.setLedgerSequence(nextSeq);
        ev.setEventType(req.getEventType());
        ev.setSourceModule(req.getSourceModule());
        ev.setSourceEntityType(req.getSourceEntityType());
        ev.setSourceEntityId(req.getSourceEntityId());
        ev.setSourceEventId(req.getSourceEventId());
        ev.setOccurredAt(occurredAt);
        ev.setRecordedAt(LocalDateTime.now());
        ev.setIdempotencyKey(req.getIdempotencyKey());
        ev.setCanonicalPayloadVersion(props.getCanonicalPayloadVersion());
        ev.setCanonicalPayloadJson(canonicalJson);
        ev.setCanonicalPayloadHash(payloadHash);
        ev.setPreviousEventHash(prevHash);
        ev.setEventHash(eventHash);
        ev.setAlgorithm(sig.algorithm());
        ev.setKeyVersion(sig.keyVersion());
        ev.setHmacSignature(sig.signatureHex());
        ev.setStatus(TransactionEvidenceEventStatus.RECORDED);
        ev.setVerificationStatus(TransactionEvidenceVerificationStatus.NOT_VERIFIED);
        ev.setMetadataJson(req.getMetadataFields() != null ? toJson(req.getMetadataFields()) : null);

        ev = eventRepository.save(ev);

        state.setLastSequence(nextSeq);
        state.setLastEventHash(ev.getEventHash());
        state.setLastEventId(ev.getId());
        state.setLastRecordedAt(ev.getRecordedAt());
        stateRepository.save(state);

        operationalEventLogService.logGenericForTenant(
                req.getTenantId(),
                OperationalEventType.TRANSACTION_EVIDENCE_EVENT_RECORDED,
                OperationalEntityType.TRANSACTION_EVIDENCE_EVENT,
                ev.getId(),
                OperationalOrigem.SYSTEM,
                "Transaction evidence event recorded",
                Map.of(
                        "tenantId", req.getTenantId(),
                        "eventId", ev.getId(),
                        "ledgerSequence", ev.getLedgerSequence(),
                        "eventType", ev.getEventType(),
                        "eventHash", ev.getEventHash(),
                        "previousEventHash", ev.getPreviousEventHash(),
                        "keyVersion", ev.getKeyVersion()
                ),
                null,
                null
        );

        return ev;
    }

    private String toJson(Map<String, Object> m) {
        try {
            return objectMapper.writeValueAsString(m);
        } catch (Exception ex) {
            return null;
        }
    }
}
