package com.restaurante.financeiro.paymentmethod.service;

import com.restaurante.config.PaymentPolicyRolloutProperties;
import com.restaurante.exception.BusinessException;
import com.restaurante.financeiro.paymentmethod.entity.*;
import com.restaurante.financeiro.paymentmethod.repository.*;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.*;
import com.restaurante.repository.TenantRepository;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentMethodPolicyRolloutWorkerService {

    private final PaymentPolicyRolloutProperties props;
    private final JdbcTemplate jdbcTemplate;
    private final TenantRepository tenantRepository;
    private final PaymentMethodPolicyRolloutRepository rolloutRepository;
    private final PaymentMethodPolicyRolloutItemRepository itemRepository;
    private final PaymentMethodPolicyTemplateRepository templateRepository;
    private final DevicePaymentMethodPolicyRepository devicePolicyRepository;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public void processOneEligibleRollout() {
        if (!props.isWorkerEnabled()) return;

        Instant now = Instant.now();
        Optional<PaymentMethodPolicyRollout> opt = rolloutRepository.findNextEligible(
                PaymentMethodPolicyRolloutExecutionMode.ASYNC,
                List.of(PaymentMethodPolicyRolloutStatus.PENDING, PaymentMethodPolicyRolloutStatus.RUNNING),
                now
        );
        if (opt.isEmpty()) return;
        PaymentMethodPolicyRollout rollout = opt.get();
        Long tenantId = rollout.getTenant().getId();

        Instant lockExpiredAt = now.minus(props.getLockTimeoutSeconds(), ChronoUnit.SECONDS);
        String lockedBy = workerId();
        int locked = rolloutRepository.tryLock(tenantId, rollout.getId(), now, lockExpiredAt, lockedBy);
        if (locked != 1) return; // outro worker pegou

        rollout = rolloutRepository.findById(rollout.getId()).orElseThrow();
        if (rollout.getStatus() == PaymentMethodPolicyRolloutStatus.PENDING) {
            rollout.setStatus(PaymentMethodPolicyRolloutStatus.RUNNING);
            rollout.setStartedAt(now);
            rollout.setProcessedBy(lockedBy);
            rollout.setLastProgressAt(now);
            rolloutRepository.save(rollout);

            Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
            if (tenant != null) {
                operationalEventLogService.logPublicEvent(
                        tenant,
                        null,
                        rollout.getUnidadeAtendimento(),
                        null,
                        null,
                        OperationalEventType.PAYMENT_POLICY_ROLLOUT_STARTED,
                        OperationalEntityType.PAYMENT_POLICY_ROLLOUT,
                        rollout.getId(),
                        OperationalOrigem.SYSTEM,
                        "Rollout assíncrono iniciado",
                        Map.ofEntries(
                                Map.entry("rolloutId", rollout.getId()),
                                Map.entry("templateId", rollout.getTemplate().getId()),
                                Map.entry("unidadeId", rollout.getUnidadeAtendimento().getId()),
                                Map.entry("batchSize", props.getBatchSize())
                        ),
                        null,
                        null
                );
            }
        }

        int processedThisRun = 0;
        while (processedThisRun < props.getBatchSize()) {
            List<Long> claimed = claimNextItemIds(tenantId, rollout.getId(), Math.min(props.getBatchSize() - processedThisRun, 50));
            if (claimed.isEmpty()) break;
            for (Long itemId : claimed) {
                try {
                    processItemRequiresNew(tenantId, itemId);
                } catch (Exception e) {
                    log.warn("Rollout item {} falhou: {}", itemId, e.getMessage());
                }
                processedThisRun++;
            }
        }

        refreshAndFinalizeIfNeeded(tenantId, rollout.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processItemRequiresNew(Long tenantId, Long itemId) {
        PaymentMethodPolicyRolloutItem item = itemRepository.findByIdAndTenant_Id(itemId, tenantId)
                .orElseThrow(() -> new BusinessException("Item não encontrado."));
        PaymentMethodPolicyRollout rollout = item.getRollout();

        try {
            PaymentMethodPolicyTemplate template = templateRepository.findWithItemsByIdAndTenant_Id(item.getTemplate().getId(), tenantId)
                    .orElseThrow(() -> new BusinessException("Template não encontrado."));
            if (template.getStatus() != PaymentMethodPolicyTemplateStatus.ACTIVE) {
                markFailed(item, "TEMPLATE_INACTIVE", "Template não está ACTIVE.");
                return;
            }

            PaymentMethodPolicyTemplateItem templateItem = item.getTemplateItem();
            if (templateItem == null) {
                markSkipped(item, PaymentMethodPolicyRolloutSkippedReason.TEMPLATE_ITEM_NOT_APPLICABLE, "Sem templateItem.");
                return;
            }

            DevicePaymentMethodPolicy existing = devicePolicyRepository
                    .findByTenant_IdAndDispositivoOperacional_IdAndPaymentMethodCode(
                            tenantId, item.getDispositivoOperacional().getId(), item.getPaymentMethodCode()
                    ).orElse(null);
            item.setPreviousPolicy(existing);
            if (existing != null) item.setManualOverrideDetected(existing.isManualOverride());

            Decision decision = decide(existing, item.getOverwriteMode());
            if (decision.action == PaymentMethodPolicyRolloutItemAction.SKIP) {
                markSkipped(item, decision.reason, decision.message);
                return;
            }

            if (existing != null && equalsPolicyToTemplate(existing, templateItem)) {
                markSkipped(item, PaymentMethodPolicyRolloutSkippedReason.POLICY_ALREADY_MATCHES, "Policy já corresponde ao template.");
                return;
            }

            Tenant tenant = rollout.getTenant();
            Instant now = Instant.now();
            if (decision.action == PaymentMethodPolicyRolloutItemAction.CREATE) {
                DevicePaymentMethodPolicy p = new DevicePaymentMethodPolicy();
                p.setTenant(tenant);
                p.setDispositivoOperacional(item.getDispositivoOperacional());
                p.setUnidadeAtendimento(item.getUnidadeAtendimento());
                p.setPaymentMethodCode(item.getPaymentMethodCode());
                applyTemplateToPolicy(p, templateItem);
                p.setInheritFromUnidade(false);
                p.setTemplateManaged(true);
                p.setManualOverride(false);
                p.setSourceTemplate(template);
                p.setSourceRollout(rollout);
                p.setTemplateAppliedAt(now);
                p.setCreatedBy(rollout.getRequestedBy());
                DevicePaymentMethodPolicy saved = devicePolicyRepository.save(p);
                item.setResultingPolicy(saved);
                item.setStatus(PaymentMethodPolicyRolloutItemStatus.CREATED);
                item.setFinishedAt(now);
                item.setErrorCode(null);
                item.setErrorMessage(null);
                itemRepository.save(item);
                return;
            }

            if (decision.action == PaymentMethodPolicyRolloutItemAction.UPDATE) {
                if (existing == null) {
                    // race: policy removida entre submit e execução
                    DevicePaymentMethodPolicy p = new DevicePaymentMethodPolicy();
                    p.setTenant(tenant);
                    p.setDispositivoOperacional(item.getDispositivoOperacional());
                    p.setUnidadeAtendimento(item.getUnidadeAtendimento());
                    p.setPaymentMethodCode(item.getPaymentMethodCode());
                    applyTemplateToPolicy(p, templateItem);
                    p.setInheritFromUnidade(false);
                    p.setTemplateManaged(true);
                    p.setManualOverride(false);
                    p.setSourceTemplate(template);
                    p.setSourceRollout(rollout);
                    p.setTemplateAppliedAt(now);
                    p.setCreatedBy(rollout.getRequestedBy());
                    DevicePaymentMethodPolicy saved = devicePolicyRepository.save(p);
                    item.setResultingPolicy(saved);
                    item.setStatus(PaymentMethodPolicyRolloutItemStatus.CREATED);
                    item.setFinishedAt(now);
                    item.setErrorCode(null);
                    item.setErrorMessage(null);
                    itemRepository.save(item);
                    return;
                }
                applyTemplateToPolicy(existing, templateItem);
                existing.setInheritFromUnidade(false);
                existing.setTemplateManaged(true);
                existing.setManualOverride(false);
                existing.setSourceTemplate(template);
                existing.setSourceRollout(rollout);
                existing.setTemplateAppliedAt(now);
                existing.setUpdatedBy(rollout.getRequestedBy());
                DevicePaymentMethodPolicy saved = devicePolicyRepository.save(existing);
                item.setResultingPolicy(saved);
                item.setStatus(PaymentMethodPolicyRolloutItemStatus.UPDATED);
                item.setFinishedAt(now);
                item.setErrorCode(null);
                item.setErrorMessage(null);
                itemRepository.save(item);
                return;
            }

            markSkipped(item, PaymentMethodPolicyRolloutSkippedReason.TEMPLATE_ITEM_NOT_APPLICABLE, "Ação não aplicável.");
        } catch (Exception e) {
            markFailed(item, "ITEM_FAILED", e.getMessage());
        }
    }

    private void refreshAndFinalizeIfNeeded(Long tenantId, Long rolloutId) {
        PaymentMethodPolicyRollout rollout = rolloutRepository.findById(rolloutId).orElseThrow();
        long total = itemRepository.countByTenant_IdAndRollout_Id(tenantId, rolloutId);
        long pending = itemRepository.countByTenant_IdAndRollout_IdAndStatus(tenantId, rolloutId, PaymentMethodPolicyRolloutItemStatus.PENDING);
        long running = itemRepository.countByTenant_IdAndRollout_IdAndStatus(tenantId, rolloutId, PaymentMethodPolicyRolloutItemStatus.RUNNING);
        long created = itemRepository.countByTenant_IdAndRollout_IdAndStatus(tenantId, rolloutId, PaymentMethodPolicyRolloutItemStatus.CREATED);
        long updated = itemRepository.countByTenant_IdAndRollout_IdAndStatus(tenantId, rolloutId, PaymentMethodPolicyRolloutItemStatus.UPDATED);
        long skipped = itemRepository.countByTenant_IdAndRollout_IdAndStatus(tenantId, rolloutId, PaymentMethodPolicyRolloutItemStatus.SKIPPED);
        long failed = itemRepository.countByTenant_IdAndRollout_IdAndStatus(tenantId, rolloutId, PaymentMethodPolicyRolloutItemStatus.FAILED);

        int succeeded = (int) (created + updated);
        int processed = (int) (created + updated + skipped + failed);

        rollout.setTotalItems((int) total);
        rollout.setPendingItems((int) (pending + running));
        rollout.setSucceededItems(succeeded);
        rollout.setSkippedItems((int) skipped);
        rollout.setFailedItems((int) failed);
        rollout.setProcessedItems(processed);
        rollout.setLastProgressAt(Instant.now());

        // status global
        if (pending == 0 && running == 0) {
            if (failed > 0 && succeeded == 0) rollout.setStatus(PaymentMethodPolicyRolloutStatus.FAILED);
            else if (failed > 0) rollout.setStatus(PaymentMethodPolicyRolloutStatus.PARTIAL_FAILED);
            else if (skipped > 0) rollout.setStatus(PaymentMethodPolicyRolloutStatus.COMPLETED_WITH_SKIPS);
            else rollout.setStatus(PaymentMethodPolicyRolloutStatus.COMPLETED);
            rollout.setFinishedAt(Instant.now());
            rollout.setLockedAt(null);
            rollout.setLockedBy(null);

            Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
            if (tenant != null) {
                OperationalEventType evt = rollout.getStatus() == PaymentMethodPolicyRolloutStatus.COMPLETED
                        ? OperationalEventType.PAYMENT_POLICY_ROLLOUT_COMPLETED
                        : rollout.getStatus() == PaymentMethodPolicyRolloutStatus.COMPLETED_WITH_SKIPS
                        ? OperationalEventType.PAYMENT_POLICY_ROLLOUT_COMPLETED
                        : rollout.getStatus() == PaymentMethodPolicyRolloutStatus.PARTIAL_FAILED
                        ? OperationalEventType.PAYMENT_POLICY_ROLLOUT_PARTIAL_FAILED
                        : OperationalEventType.PAYMENT_POLICY_ROLLOUT_FAILED;
                operationalEventLogService.logPublicEvent(
                        tenant,
                        null,
                        rollout.getUnidadeAtendimento(),
                        null,
                        null,
                        evt,
                        OperationalEntityType.PAYMENT_POLICY_ROLLOUT,
                        rollout.getId(),
                        OperationalOrigem.SYSTEM,
                        "Rollout assíncrono finalizado",
                        Map.ofEntries(
                                Map.entry("rolloutId", rollout.getId()),
                                Map.entry("status", rollout.getStatus().name()),
                                Map.entry("totalItems", rollout.getTotalItems()),
                                Map.entry("processedItems", rollout.getProcessedItems()),
                                Map.entry("succeededItems", rollout.getSucceededItems()),
                                Map.entry("skippedItems", rollout.getSkippedItems()),
                                Map.entry("failedItems", rollout.getFailedItems())
                        ),
                        null,
                        null
                );
            }
        } else {
            // progresso incremental (rate-limit simples por batch)
            Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
            if (tenant != null) {
                operationalEventLogService.logPublicEvent(
                        tenant,
                        null,
                        rollout.getUnidadeAtendimento(),
                        null,
                        null,
                        OperationalEventType.PAYMENT_POLICY_ROLLOUT_PROGRESS_UPDATED,
                        OperationalEntityType.PAYMENT_POLICY_ROLLOUT,
                        rollout.getId(),
                        OperationalOrigem.SYSTEM,
                        "Progresso de rollout atualizado",
                        Map.ofEntries(
                                Map.entry("rolloutId", rollout.getId()),
                                Map.entry("processedItems", rollout.getProcessedItems()),
                                Map.entry("totalItems", rollout.getTotalItems()),
                                Map.entry("failedItems", rollout.getFailedItems())
                        ),
                        null,
                        null
                );
            }
        }

        rolloutRepository.save(rollout);
    }

    private List<Long> claimNextItemIds(Long tenantId, Long rolloutId, int limit) {
        int maxAttempts = props.getMaxAttempts();
        String sql = """
                with cte as (
                    select id
                      from payment_method_policy_rollout_items
                     where tenant_id = ?
                       and rollout_id = ?
                       and status in ('PENDING','FAILED')
                       and attempts < ?
                     order by id asc
                     for update skip locked
                     limit ?
                )
                update payment_method_policy_rollout_items i
                   set status = 'RUNNING',
                       started_at = coalesce(i.started_at, now()),
                       attempts = i.attempts + 1,
                       updated_at = now()
                  from cte
                 where i.id = cte.id
                returning i.id
                """;
        return jdbcTemplate.query(sql, rs -> {
            List<Long> ids = new ArrayList<>();
            while (rs.next()) ids.add(rs.getLong(1));
            return ids;
        }, tenantId, rolloutId, maxAttempts, limit);
    }

    private Decision decide(DevicePaymentMethodPolicy existing, PaymentMethodPolicyOverwriteMode overwriteMode) {
        if (existing == null) return Decision.create();
        if (overwriteMode == PaymentMethodPolicyOverwriteMode.SKIP_EXISTING) {
            return Decision.skip(PaymentMethodPolicyRolloutSkippedReason.EXISTING_POLICY_SKIP_MODE, "SKIP_EXISTING: policy já existe.");
        }
        if (overwriteMode == PaymentMethodPolicyOverwriteMode.OVERWRITE_EXISTING) {
            return Decision.update();
        }
        if (overwriteMode == PaymentMethodPolicyOverwriteMode.OVERWRITE_ONLY_TEMPLATE_MANAGED) {
            if (existing.isManualOverride()) {
                return Decision.skip(PaymentMethodPolicyRolloutSkippedReason.MANUAL_OVERRIDE_PROTECTED, "manualOverride=true protegido.");
            }
            return (existing.isTemplateManaged()) ? Decision.update()
                    : Decision.skip(PaymentMethodPolicyRolloutSkippedReason.MANUAL_OVERRIDE_PROTECTED, "policy não é templateManaged.");
        }
        return Decision.skip(PaymentMethodPolicyRolloutSkippedReason.TEMPLATE_ITEM_NOT_APPLICABLE, "overwriteMode inválido.");
    }

    private boolean equalsPolicyToTemplate(DevicePaymentMethodPolicy p, PaymentMethodPolicyTemplateItem ti) {
        return Objects.equals(p.getStatus(), ti.getPolicyStatus())
                && Objects.equals(p.getEnabledForPos(), ti.getEnabledForPos())
                && Objects.equals(p.getEnabledForPedido(), ti.getEnabledForPedido())
                && Objects.equals(p.getEnabledForFundoConsumo(), ti.getEnabledForFundoConsumo())
                && Objects.equals(p.getCanConfirmManual(), ti.getCanConfirmManual())
                && Objects.equals(p.getCanStartGateway(), ti.getCanStartGateway())
                && Objects.equals(p.getMinAmount(), ti.getMinAmount())
                && Objects.equals(p.getMaxAmount(), ti.getMaxAmount())
                && Objects.equals(p.getOverrideReason(), ti.getOverrideReason())
                && Objects.equals(p.getMetadataJson(), ti.getMetadataJson());
    }

    private void applyTemplateToPolicy(DevicePaymentMethodPolicy policy, PaymentMethodPolicyTemplateItem item) {
        if ((item.getPaymentMethodCode() == PaymentMethodCode.CASH || item.getPaymentMethodCode() == PaymentMethodCode.TPA)
                && Boolean.TRUE.equals(item.getCanStartGateway())) {
            throw new BusinessException("Item inválido: CASH/TPA não podem ter canStartGateway=true.");
        }
        if (item.getPaymentMethodCode() == PaymentMethodCode.APPYPAY && Boolean.TRUE.equals(item.getCanConfirmManual())) {
            throw new BusinessException("Item inválido: APPYPAY não pode ter canConfirmManual=true.");
        }
        policy.setStatus(item.getPolicyStatus());
        policy.setEnabledForPos(item.getEnabledForPos());
        policy.setEnabledForPedido(item.getEnabledForPedido());
        policy.setEnabledForFundoConsumo(item.getEnabledForFundoConsumo());
        policy.setCanConfirmManual(item.getCanConfirmManual());
        policy.setCanStartGateway(item.getCanStartGateway());
        policy.setMinAmount(item.getMinAmount());
        policy.setMaxAmount(item.getMaxAmount());
        policy.setOverrideReason(item.getOverrideReason());
        policy.setMetadataJson(item.getMetadataJson());
    }

    private void markSkipped(PaymentMethodPolicyRolloutItem item, PaymentMethodPolicyRolloutSkippedReason reason, String msg) {
        item.setStatus(PaymentMethodPolicyRolloutItemStatus.SKIPPED);
        item.setSkippedReason(reason);
        item.setErrorCode(null);
        item.setErrorMessage(null);
        item.setFinishedAt(Instant.now());
        itemRepository.save(item);
    }

    private void markFailed(PaymentMethodPolicyRolloutItem item, String code, String msg) {
        int attempts = item.getAttempts();
        if (attempts >= props.getMaxAttempts()) {
            item.setStatus(PaymentMethodPolicyRolloutItemStatus.FAILED);
        } else {
            item.setStatus(PaymentMethodPolicyRolloutItemStatus.PENDING);
        }
        item.setErrorCode(code);
        item.setErrorMessage(msg);
        item.setFinishedAt(Instant.now());
        itemRepository.save(item);

        Tenant tenant = item.getTenant();
        operationalEventLogService.logPublicEvent(
                tenant,
                null,
                item.getUnidadeAtendimento(),
                null,
                null,
                OperationalEventType.PAYMENT_POLICY_ROLLOUT_ITEM_FAILED,
                OperationalEntityType.PAYMENT_POLICY_ROLLOUT,
                item.getRollout().getId(),
                OperationalOrigem.SYSTEM,
                "Rollout item falhou",
                Map.ofEntries(
                        Map.entry("rolloutId", item.getRollout().getId()),
                        Map.entry("itemId", item.getId()),
                        Map.entry("deviceId", item.getDispositivoOperacional().getId()),
                        Map.entry("paymentMethodCode", item.getPaymentMethodCode().name()),
                        Map.entry("attempts", item.getAttempts()),
                        Map.entry("errorCode", code)
                ),
                null,
                null
        );
    }

    private String workerId() {
        return "worker-" + UUID.randomUUID();
    }

    static class Decision {
        final PaymentMethodPolicyRolloutItemAction action;
        final PaymentMethodPolicyRolloutSkippedReason reason;
        final String message;

        private Decision(PaymentMethodPolicyRolloutItemAction action, PaymentMethodPolicyRolloutSkippedReason reason, String message) {
            this.action = action;
            this.reason = reason;
            this.message = message;
        }

        static Decision create() { return new Decision(PaymentMethodPolicyRolloutItemAction.CREATE, null, null); }
        static Decision update() { return new Decision(PaymentMethodPolicyRolloutItemAction.UPDATE, null, null); }
        static Decision skip(PaymentMethodPolicyRolloutSkippedReason reason, String message) {
            return new Decision(PaymentMethodPolicyRolloutItemAction.SKIP, reason, message);
        }
    }
}

