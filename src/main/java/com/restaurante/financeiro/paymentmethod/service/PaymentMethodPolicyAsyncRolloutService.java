package com.restaurante.financeiro.paymentmethod.service;

import com.restaurante.config.PaymentPolicyRolloutProperties;
import com.restaurante.dto.request.PaymentPolicyRolloutRequest;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.financeiro.paymentmethod.entity.*;
import com.restaurante.financeiro.paymentmethod.repository.*;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.*;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PaymentMethodPolicyAsyncRolloutService {

    private final TenantGuard tenantGuard;
    private final PaymentPolicyRolloutProperties props;
    private final TenantRepository tenantRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;
    private final PaymentMethodPolicyTemplateRepository templateRepository;
    private final DevicePaymentMethodPolicyRepository devicePolicyRepository;
    private final PaymentMethodPolicyRolloutRepository rolloutRepository;
    private final PaymentMethodPolicyRolloutItemRepository itemRepository;
    private final OperationalEventLogService operationalEventLogService;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentMethodPolicyRollout submit(Long templateId, PaymentPolicyRolloutRequest req, String idempotencyKey, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = TenantContextHolder.require();
        if (!props.isAsyncEnabled()) throw new BusinessException("Rollout assíncrono está desativado.");

        Tenant tenant = tenantRepository.findById(ctx.tenantId()).orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        PaymentMethodPolicyTemplate template = templateRepository.findWithItemsByIdAndTenant_Id(templateId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Template não encontrado."));
        if (template.getStatus() != PaymentMethodPolicyTemplateStatus.ACTIVE) throw new BusinessException("Template não pode ser submetido: status " + template.getStatus().name());

        UnidadeAtendimento unidade = requireUnidadeOfTenant(ctx.tenantId(), req.getUnidadeId());

        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        String payloadHash = computeIdempotencyPayloadHash(template.getId(), req);
        if (normalizedKey != null) {
            rolloutRepository.findByTenant_IdAndIdempotencyKey(ctx.tenantId(), normalizedKey).ifPresent(existing -> {
                String existingHash = readIdempotencyPayloadHash(existing.getResultJson());
                if (existingHash != null && !existingHash.equals(payloadHash)) {
                    throw new BusinessException("Idempotency-Key já usado com payload diferente.");
                }
                throw new ExistingRolloutException(existing);
            });
        }

        List<DispositivoOperacional> devices = resolveTargetDevices(ctx.tenantId(), unidade.getId(), template, req);
        if (devices.isEmpty()) throw new BusinessException("Nenhum device alvo encontrado para rollout.");
        List<PaymentMethodPolicyTemplateItem> templateItems = template.getItems() != null ? template.getItems() : List.of();
        if (templateItems.isEmpty()) throw new BusinessException("Template não possui itens.");

        Set<Long> deviceIds = new LinkedHashSet<>();
        devices.forEach(d -> deviceIds.add(d.getId()));
        List<DevicePaymentMethodPolicy> existingPolicies = devicePolicyRepository.findByTenantAndUnidadeAndDeviceIds(ctx.tenantId(), unidade.getId(), deviceIds);
        Map<Long, Map<PaymentMethodCode, DevicePaymentMethodPolicy>> existingByDevice = new HashMap<>();
        for (DevicePaymentMethodPolicy p : existingPolicies) {
            existingByDevice.computeIfAbsent(p.getDispositivoOperacional().getId(), k -> new EnumMap<>(PaymentMethodCode.class))
                    .put(p.getPaymentMethodCode(), p);
        }

        Instant now = Instant.now();
        PaymentMethodPolicyRollout rollout = new PaymentMethodPolicyRollout();
        rollout.setTenant(tenant);
        rollout.setTemplate(template);
        rollout.setUnidadeAtendimento(unidade);
        rollout.setTargetDeviceType(resolveEffectiveTargetType(template, req));
        rollout.setRolloutMode(req.getRolloutMode());
        rollout.setOverwriteMode(req.getOverwriteMode());
        rollout.setDryRun(false);
        rollout.setExecutionMode(PaymentMethodPolicyRolloutExecutionMode.ASYNC);
        rollout.setStatus(PaymentMethodPolicyRolloutStatus.PENDING);
        rollout.setRequestedAt(now);
        rollout.setRequestedBy(ctx.userId());
        rollout.setIdempotencyKey(normalizedKey);
        rollout.setTotalDevicesTargeted(devices.size());
        rollout.setResultJson(writeJson(Map.of("idempotencyPayloadHash", payloadHash)));
        rollout = rolloutRepository.save(rollout);

        int totalItems = 0;
        List<PaymentMethodPolicyRolloutItem> itemsToSave = new ArrayList<>();
        for (DispositivoOperacional d : devices) {
            Map<PaymentMethodCode, DevicePaymentMethodPolicy> exMap = existingByDevice.getOrDefault(d.getId(), new EnumMap<>(PaymentMethodCode.class));
            for (PaymentMethodPolicyTemplateItem ti : templateItems) {
                DevicePaymentMethodPolicy ex = exMap.get(ti.getPaymentMethodCode());
                PaymentMethodPolicyRolloutItemAction planned = planAction(ex, req.getOverwriteMode());

                PaymentMethodPolicyRolloutItem item = new PaymentMethodPolicyRolloutItem();
                item.setTenant(tenant);
                item.setRollout(rollout);
                item.setTemplate(template);
                item.setUnidadeAtendimento(unidade);
                item.setDispositivoOperacional(d);
                item.setPaymentMethodCode(ti.getPaymentMethodCode());
                item.setTemplateItem(ti);
                item.setOverwriteMode(req.getOverwriteMode());
                item.setPlannedAction(planned);
                item.setStatus(PaymentMethodPolicyRolloutItemStatus.PENDING);
                item.setPreviousPolicy(ex);
                if (ex != null) item.setManualOverrideDetected(ex.isManualOverride());
                itemsToSave.add(item);
                totalItems++;
            }
        }
        itemRepository.saveAll(itemsToSave);

        rollout.setTotalItems(totalItems);
        rollout.setPendingItems(totalItems);
        rollout.setProcessedItems(0);
        rollout.setSucceededItems(0);
        rollout.setSkippedItems(0);
        rollout.setFailedItems(0);
        rollout.setLastProgressAt(now);
        rolloutRepository.save(rollout);

        operationalEventLogService.logPublicEvent(
                tenant,
                null,
                unidade,
                null,
                null,
                OperationalEventType.PAYMENT_POLICY_ROLLOUT_SUBMITTED,
                OperationalEntityType.PAYMENT_POLICY_ROLLOUT,
                rollout.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Rollout assíncrono submetido",
                Map.ofEntries(
                        Map.entry("rolloutId", rollout.getId()),
                        Map.entry("templateId", template.getId()),
                        Map.entry("templateCode", template.getCode()),
                        Map.entry("unidadeId", unidade.getId()),
                        Map.entry("rolloutMode", req.getRolloutMode().name()),
                        Map.entry("overwriteMode", req.getOverwriteMode().name()),
                        Map.entry("executionMode", rollout.getExecutionMode().name()),
                        Map.entry("totalDevicesTargeted", rollout.getTotalDevicesTargeted()),
                        Map.entry("totalItems", rollout.getTotalItems())
                ),
                ip,
                userAgent
        );

        return rollout;
    }

    @Transactional(readOnly = true)
    public PaymentMethodPolicyRollout getRollout(Long rolloutId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE, TenantUserRole.TENANT_CASHIER);
        TenantContext ctx = tenantGuard.requireContext();
        return rolloutRepository.findByIdAndTenant_Id(rolloutId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Rollout não encontrado."));
    }

    @Transactional(readOnly = true)
    public Page<PaymentMethodPolicyRolloutItem> pageItems(Long rolloutId, PaymentMethodPolicyRolloutItemStatus status, int page, int size) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE, TenantUserRole.TENANT_CASHIER);
        TenantContext ctx = tenantGuard.requireContext();
        // garante tenant-safe
        getRollout(rolloutId);
        return itemRepository.pageByRolloutAndStatus(ctx.tenantId(), rolloutId, status, PageRequest.of(page, size));
    }

    @Transactional
    public PaymentMethodPolicyRollout rerun(Long rolloutId, Set<PaymentMethodPolicyRolloutItemStatus> onlyStatuses, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = TenantContextHolder.require();
        Tenant tenant = tenantRepository.findById(ctx.tenantId()).orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        PaymentMethodPolicyRollout rollout = rolloutRepository.findByIdAndTenant_Id(rolloutId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Rollout não encontrado."));
        if (rollout.getExecutionMode() != PaymentMethodPolicyRolloutExecutionMode.ASYNC) throw new BusinessException("Rerun só é suportado para rollouts ASYNC.");

        if (rollout.getStatus() == PaymentMethodPolicyRolloutStatus.PENDING || rollout.getStatus() == PaymentMethodPolicyRolloutStatus.RUNNING) {
            throw new BusinessException("Rerun não permitido: rollout ainda está PENDING/RUNNING.");
        }

        Set<PaymentMethodPolicyRolloutItemStatus> statuses = (onlyStatuses == null || onlyStatuses.isEmpty())
                ? EnumSet.of(PaymentMethodPolicyRolloutItemStatus.FAILED, PaymentMethodPolicyRolloutItemStatus.PENDING)
                : EnumSet.copyOf(onlyStatuses);

        // reset FAILED/PENDING -> PENDING (preservando CREATED/UPDATED/SKIPPED)
        // não reprocessa SKIPPED por manualOverride por padrão
        int resetCount = 0;
        // simples: pagear e atualizar; MVP suficiente
        int page = 0;
        while (true) {
            Page<PaymentMethodPolicyRolloutItem> p = itemRepository.pageByRolloutAndStatus(ctx.tenantId(), rolloutId, null, PageRequest.of(page, 500));
            if (p.isEmpty()) break;
            for (PaymentMethodPolicyRolloutItem item : p.getContent()) {
                if (!statuses.contains(item.getStatus())) continue;
                if (item.getStatus() == PaymentMethodPolicyRolloutItemStatus.SKIPPED && item.isManualOverrideDetected()) continue;
                if (item.getStatus() == PaymentMethodPolicyRolloutItemStatus.CREATED || item.getStatus() == PaymentMethodPolicyRolloutItemStatus.UPDATED) continue;
                item.setStatus(PaymentMethodPolicyRolloutItemStatus.PENDING);
                item.setErrorCode(null);
                item.setErrorMessage(null);
                item.setStartedAt(null);
                item.setFinishedAt(null);
                resetCount++;
            }
            itemRepository.saveAll(p.getContent());
            if (!p.hasNext()) break;
            page++;
        }

        rollout.setRetryCount(rollout.getRetryCount() + 1);
        rollout.setStatus(PaymentMethodPolicyRolloutStatus.PENDING);
        rollout.setNextRetryAt(null);
        rollout.setLastError(null);
        rollout.setLockedAt(null);
        rollout.setLockedBy(null);
        rollout.setLastProgressAt(Instant.now());
        rolloutRepository.save(rollout);

        operationalEventLogService.logPublicEvent(
                tenant,
                null,
                rollout.getUnidadeAtendimento(),
                null,
                null,
                OperationalEventType.PAYMENT_POLICY_ROLLOUT_RERUN_REQUESTED,
                OperationalEntityType.PAYMENT_POLICY_ROLLOUT,
                rollout.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Rerun de rollout solicitado",
                Map.ofEntries(
                        Map.entry("rolloutId", rollout.getId()),
                        Map.entry("retryCount", rollout.getRetryCount()),
                        Map.entry("resetItems", resetCount)
                ),
                ip,
                userAgent
        );

        return rollout;
    }

    private UnidadeAtendimento requireUnidadeOfTenant(Long tenantId, Long unidadeId) {
        UnidadeAtendimento u = unidadeAtendimentoRepository.findById(unidadeId)
                .orElseThrow(() -> new ResourceNotFoundException("Unidade não encontrada."));
        Long tId = u.getInstituicao() != null && u.getInstituicao().getTenant() != null ? u.getInstituicao().getTenant().getId() : null;
        if (!Objects.equals(tId, tenantId)) throw new ResourceNotFoundException("Unidade não encontrada.");
        return u;
    }

    private List<DispositivoOperacional> resolveTargetDevices(Long tenantId, Long unidadeId, PaymentMethodPolicyTemplate template, PaymentPolicyRolloutRequest req) {
        OperationalDeviceType effectiveType = resolveEffectiveTargetType(template, req);
        if (req.getRolloutMode() == PaymentMethodPolicyRolloutMode.UNIT_ALL_DEVICES) {
            return dispositivoOperacionalRepository.findByTenantAndUnidadeAtendimentoAndOperationalType(tenantId, unidadeId, null);
        }
        if (req.getRolloutMode() == PaymentMethodPolicyRolloutMode.UNIT_BY_DEVICE_TYPE) {
            if (effectiveType == null) throw new BusinessException("targetDeviceType é obrigatório para rollout por tipo.");
            return dispositivoOperacionalRepository.findByTenantAndUnidadeAtendimentoAndOperationalType(tenantId, unidadeId, effectiveType);
        }
        if (req.getRolloutMode() == PaymentMethodPolicyRolloutMode.SELECTED_DEVICES) {
            List<Long> ids = req.getSelectedDeviceIds();
            if (ids == null || ids.isEmpty()) throw new BusinessException("selectedDeviceIds é obrigatório para rollout SELECTED_DEVICES.");
            List<DispositivoOperacional> found = dispositivoOperacionalRepository.findByTenantAndUnidadeAtendimentoAndIds(tenantId, unidadeId, ids);
            if (found.size() != new HashSet<>(ids).size()) {
                throw new BusinessException("selectedDeviceIds contém devices inexistentes, de outro tenant ou de outra unidade.");
            }
            return found;
        }
        throw new BusinessException("rolloutMode inválido.");
    }

    private OperationalDeviceType resolveEffectiveTargetType(PaymentMethodPolicyTemplate template, PaymentPolicyRolloutRequest req) {
        if (req.getRolloutMode() == PaymentMethodPolicyRolloutMode.UNIT_BY_DEVICE_TYPE) {
            if (req.getTargetDeviceType() != null) return req.getTargetDeviceType();
            return template.getTargetDeviceType();
        }
        return req.getTargetDeviceType();
    }

    private PaymentMethodPolicyRolloutItemAction planAction(DevicePaymentMethodPolicy existing, PaymentMethodPolicyOverwriteMode overwriteMode) {
        if (existing == null) return PaymentMethodPolicyRolloutItemAction.CREATE;
        if (overwriteMode == PaymentMethodPolicyOverwriteMode.SKIP_EXISTING) return PaymentMethodPolicyRolloutItemAction.SKIP;
        if (overwriteMode == PaymentMethodPolicyOverwriteMode.OVERWRITE_EXISTING) return PaymentMethodPolicyRolloutItemAction.UPDATE;
        if (overwriteMode == PaymentMethodPolicyOverwriteMode.OVERWRITE_ONLY_TEMPLATE_MANAGED) {
            return (existing.isTemplateManaged() && !existing.isManualOverride())
                    ? PaymentMethodPolicyRolloutItemAction.UPDATE
                    : PaymentMethodPolicyRolloutItemAction.SKIP;
        }
        return PaymentMethodPolicyRolloutItemAction.SKIP;
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null) return null;
        String k = key.trim();
        if (k.isBlank()) return null;
        if (k.length() > 120) throw new BusinessException("idempotencyKey excede 120 caracteres.");
        return k;
    }

    private String computeIdempotencyPayloadHash(Long templateId, PaymentPolicyRolloutRequest req) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String canonical = "templateId=" + templateId
                    + "|unidadeId=" + req.getUnidadeId()
                    + "|rolloutMode=" + (req.getRolloutMode() != null ? req.getRolloutMode().name() : "null")
                    + "|targetDeviceType=" + (req.getTargetDeviceType() != null ? req.getTargetDeviceType().name() : "null")
                    + "|overwriteMode=" + (req.getOverwriteMode() != null ? req.getOverwriteMode().name() : "null")
                    + "|selectedDeviceIds=" + canonicalizeIds(req.getSelectedDeviceIds());
            byte[] digest = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String canonicalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return "";
        List<Long> sorted = new ArrayList<>(ids);
        sorted.sort(Comparator.naturalOrder());
        return sorted.toString();
    }

    private String readIdempotencyPayloadHash(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) return null;
        try {
            JsonNode n = objectMapper.readTree(resultJson);
            String v = n.path("idempotencyPayloadHash").asText(null);
            return (v == null || v.isBlank()) ? null : v;
        } catch (Exception e) {
            return null;
        }
    }

    private String writeJson(Map<String, Object> json) {
        if (json == null) return null;
        try {
            return objectMapper.writeValueAsString(json);
        } catch (Exception e) {
            return null;
        }
    }

    // Exception interna para fluxo idempotente (retornar rollout existente sem duplicar)
    public static class ExistingRolloutException extends RuntimeException {
        private final PaymentMethodPolicyRollout rollout;
        public ExistingRolloutException(PaymentMethodPolicyRollout rollout) { this.rollout = rollout; }
        public PaymentMethodPolicyRollout getRollout() { return rollout; }
    }
}
