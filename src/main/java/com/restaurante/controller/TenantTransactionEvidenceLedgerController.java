package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.TransactionEvidenceEvent;
import com.restaurante.model.entity.TransactionEvidenceLedgerState;
import com.restaurante.model.entity.TransactionEvidenceVerificationIssue;
import com.restaurante.model.entity.TransactionEvidenceVerificationRun;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TransactionEvidenceSourceModule;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.txevidence.dto.request.VerifyTransactionLedgerRequest;
import com.restaurante.txevidence.dto.response.TransactionEvidenceEventResponse;
import com.restaurante.txevidence.dto.response.TransactionEvidenceLedgerStateResponse;
import com.restaurante.txevidence.dto.response.TransactionEvidenceVerificationIssueResponse;
import com.restaurante.txevidence.dto.response.TransactionEvidenceVerificationRunResponse;
import com.restaurante.txevidence.repository.TransactionEvidenceEventRepository;
import com.restaurante.txevidence.repository.TransactionEvidenceLedgerStateRepository;
import com.restaurante.txevidence.repository.TransactionEvidenceVerificationIssueRepository;
import com.restaurante.txevidence.repository.TransactionEvidenceVerificationRunRepository;
import com.restaurante.txevidence.service.TransactionEvidenceVerificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/tenant/evidence/transaction-ledger")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Tenant Transaction Evidence Ledger", description = "Ledger transacional append-only (tenant/admin)")
public class TenantTransactionEvidenceLedgerController {

    private final TenantGuard tenantGuard;
    private final TransactionEvidenceEventRepository eventRepository;
    private final TransactionEvidenceLedgerStateRepository stateRepository;
    private final TransactionEvidenceVerificationRunRepository runRepository;
    private final TransactionEvidenceVerificationIssueRepository issueRepository;
    private final TransactionEvidenceVerificationService verificationService;

    @GetMapping("/events")
    public ResponseEntity<ApiResponse<Page<TransactionEvidenceEventResponse>>> events(@RequestParam(required = false) String eventType,
                                                                                    @RequestParam(required = false) TransactionEvidenceSourceModule sourceModule,
                                                                                    @RequestParam(required = false) String sourceEntityType,
                                                                                    @RequestParam(required = false) Long sourceEntityId,
                                                                                    @RequestParam(required = false) LocalDateTime occurredFrom,
                                                                                    @RequestParam(required = false) LocalDateTime occurredTo,
                                                                                    @RequestParam(required = false) Long sequenceFrom,
                                                                                    @RequestParam(required = false) Long sequenceTo,
                                                                                    Pageable pageable) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        Page<TransactionEvidenceEventResponse> page = eventRepository.search(
                        ctx.tenantId(),
                        eventType,
                        sourceModule,
                        sourceEntityType,
                        sourceEntityId,
                        occurredFrom,
                        occurredTo,
                        sequenceFrom,
                        sequenceTo,
                        pageable
                )
                .map(this::mapList);
        return ResponseEntity.ok(ApiResponse.success("Ledger events", page));
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<ApiResponse<TransactionEvidenceEventResponse>> event(@PathVariable Long eventId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        TransactionEvidenceEvent ev = eventRepository.findByTenantIdAndId(ctx.tenantId(), eventId)
                .orElseThrow(() -> new BusinessException("TRANSACTION_EVIDENCE_EVENT_NOT_FOUND"));
        return ResponseEntity.ok(ApiResponse.success("Ledger event", mapDetail(ev)));
    }

    @GetMapping("/state")
    public ResponseEntity<ApiResponse<TransactionEvidenceLedgerStateResponse>> state() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        TransactionEvidenceLedgerState st = stateRepository.findByTenantId(ctx.tenantId()).orElse(null);
        return ResponseEntity.ok(ApiResponse.success("Ledger state", st != null ? map(st) : null));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<TransactionEvidenceVerificationRunResponse>> verify(@Valid @RequestBody VerifyTransactionLedgerRequest req) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        if (req == null || req.getPeriodStart() == null || req.getPeriodEnd() == null) {
            throw new BusinessException("TRANSACTION_EVIDENCE_VERIFICATION_FAILED");
        }
        TransactionEvidenceVerificationRun run = verificationService.verifyTenantLedger(ctx.tenantId(), req.getPeriodStart(), req.getPeriodEnd());
        return ResponseEntity.ok(ApiResponse.success("Verification run", map(run)));
    }

    @GetMapping("/verification-runs")
    public ResponseEntity<ApiResponse<List<TransactionEvidenceVerificationRunResponse>>> runs() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        List<TransactionEvidenceVerificationRunResponse> out = runRepository.findByTenantIdOrderByStartedAtDesc(ctx.tenantId())
                .stream().limit(50).map(this::map).toList();
        return ResponseEntity.ok(ApiResponse.success("Verification runs", out));
    }

    @GetMapping("/verification-runs/{runId}")
    public ResponseEntity<ApiResponse<TransactionEvidenceVerificationRunResponse>> run(@PathVariable Long runId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        TransactionEvidenceVerificationRun run = runRepository.findByTenantIdAndId(ctx.tenantId(), runId)
                .orElseThrow(() -> new BusinessException("TRANSACTION_EVIDENCE_VERIFICATION_FAILED"));
        return ResponseEntity.ok(ApiResponse.success("Verification run", map(run)));
    }

    @GetMapping("/verification-runs/{runId}/issues")
    public ResponseEntity<ApiResponse<List<TransactionEvidenceVerificationIssueResponse>>> issues(@PathVariable Long runId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        var ctx = tenantGuard.requireContext();
        List<TransactionEvidenceVerificationIssueResponse> out = issueRepository.findByTenantIdAndVerificationRun_IdOrderByIdAsc(ctx.tenantId(), runId)
                .stream().limit(500).map(this::map).toList();
        return ResponseEntity.ok(ApiResponse.success("Verification issues", out));
    }

    private TransactionEvidenceEventResponse mapList(TransactionEvidenceEvent e) {
        return new TransactionEvidenceEventResponse(
                e.getId(),
                e.getLedgerSequence(),
                e.getEventType(),
                e.getSourceModule(),
                e.getSourceEntityType(),
                e.getSourceEntityId(),
                e.getSourceEventId(),
                e.getOccurredAt(),
                e.getRecordedAt(),
                e.getIdempotencyKey(),
                e.getCanonicalPayloadVersion(),
                e.getCanonicalPayloadHash(),
                e.getPreviousEventHash(),
                e.getEventHash(),
                e.getKeyVersion(),
                e.getAlgorithm(),
                e.getStatus(),
                e.getVerificationStatus(),
                null
        );
    }

    private TransactionEvidenceEventResponse mapDetail(TransactionEvidenceEvent e) {
        TransactionEvidenceEventResponse base = mapList(e);
        return new TransactionEvidenceEventResponse(
                base.getId(),
                base.getLedgerSequence(),
                base.getEventType(),
                base.getSourceModule(),
                base.getSourceEntityType(),
                base.getSourceEntityId(),
                base.getSourceEventId(),
                base.getOccurredAt(),
                base.getRecordedAt(),
                base.getIdempotencyKey(),
                base.getCanonicalPayloadVersion(),
                base.getCanonicalPayloadHash(),
                base.getPreviousEventHash(),
                base.getEventHash(),
                base.getKeyVersion(),
                base.getAlgorithm(),
                base.getStatus(),
                base.getVerificationStatus(),
                e.getCanonicalPayloadJson()
        );
    }

    private TransactionEvidenceLedgerStateResponse map(TransactionEvidenceLedgerState s) {
        return new TransactionEvidenceLedgerStateResponse(
                s.getId(),
                s.getLastSequence(),
                s.getLastEventHash(),
                s.getLastEventId(),
                s.getLastRecordedAt(),
                s.getStatus()
        );
    }

    private TransactionEvidenceVerificationRunResponse map(TransactionEvidenceVerificationRun r) {
        return new TransactionEvidenceVerificationRunResponse(
                r.getId(),
                r.getPeriodStart(),
                r.getPeriodEnd(),
                r.getStatus(),
                r.getCheckedEventsCount(),
                r.getInvalidEventsCount(),
                r.getBrokenChainCount(),
                r.getSequenceGapCount(),
                r.getStartedAt(),
                r.getFinishedAt(),
                r.getReportHash()
        );
    }

    private TransactionEvidenceVerificationIssueResponse map(TransactionEvidenceVerificationIssue i) {
        return new TransactionEvidenceVerificationIssueResponse(
                i.getId(),
                i.getEvent() != null ? i.getEvent().getId() : null,
                i.getLedgerSequence(),
                i.getIssueType(),
                i.getDescription(),
                i.getDetectedAt()
        );
    }
}

