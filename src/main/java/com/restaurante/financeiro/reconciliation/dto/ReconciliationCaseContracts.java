package com.restaurante.financeiro.reconciliation.dto;

import com.restaurante.financeiro.reconciliation.model.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ReconciliationCaseContracts {
    private ReconciliationCaseContracts() {}
    public record CommandContext(Long version, @NotBlank @Size(max=1000) String reason) {}
    public record AssignRequest(Long version, @NotNull Long assignedToUserId, @NotBlank @Size(max=1000) String reason) {}
    public record NoteRequest(Long version, @NotBlank @Size(max=4000) String content, @NotNull ReconciliationNoteType type, @NotNull ReconciliationNoteVisibility visibility) {}
    public record ClassifyRequest(Long version, @NotNull ReconciliationCaseClassification classification, @NotBlank @Size(max=1000) String reason) {}
    public record CloseRequest(Long version, @NotBlank @Pattern(regexp="EXTERNAL_REFUND|HISTORICAL_DATA|ACCOUNTING|NOT_AUTOMATICALLY_RECONCILABLE") String resolution, @NotBlank @Size(max=1000) String reason) {}
    public record TenantRef(Long id, String code) {}
    public record Assignee(Long id, String username) {}
    public record PolicyCheck(String code, boolean passed, String detail) {}
    public record Eligibility(boolean retryPossible, List<String> allowedActions, List<PolicyCheck> policyChecks, String remoteStatus, String localStatus, String pedidoStatus, BigDecimal localAmount, String localReference, List<String> blockers) {}
    public record Event(Long id, ReconciliationCaseAction action, Long actorUserId, String actorRoles, String actorOrigin, String reason, ReconciliationNoteType noteType, ReconciliationNoteVisibility visibility, String correlationId, LocalDateTime timestamp) {}
    public record Summary(Long caseId, Long version, TenantRef tenant, Long pagamentoId, Long pedidoId, String externalReference, String remoteStatus, String localStatus, String reconciliationStatus, ReconciliationCaseStatus status, ReconciliationCaseClassification classification, Assignee assignedTo, LocalDateTime openedAt, LocalDateTime updatedAt, long ageHours, String attentionLevel) {}
    public record Detail(Summary summary, String technicalReason, int attempts, LocalDateTime lastAttemptAt, String responseFingerprint, Eligibility eligibility, List<Event> administrativeHistory, boolean gatewayPayloadIncluded) {}
    public record BackfillResult(boolean dryRun, long eligible, long created, long alreadyExisting) {}
    public record MaterializeRequest(@NotBlank @Size(max=1000) String reason) {}
}
