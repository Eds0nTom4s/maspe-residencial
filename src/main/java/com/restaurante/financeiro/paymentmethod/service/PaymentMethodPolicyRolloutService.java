package com.restaurante.financeiro.paymentmethod.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.PaymentPolicyRolloutRequest;
import com.restaurante.dto.response.DevicePolicyRolloutResultResponse;
import com.restaurante.dto.response.PaymentPolicyRolloutApplyResponse;
import com.restaurante.dto.response.PaymentPolicyRolloutPreviewResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.financeiro.paymentmethod.entity.*;
import com.restaurante.financeiro.paymentmethod.repository.DevicePaymentMethodPolicyRepository;
import com.restaurante.financeiro.paymentmethod.repository.PaymentMethodPolicyRolloutRepository;
import com.restaurante.financeiro.paymentmethod.repository.PaymentMethodPolicyTemplateRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PaymentMethodPolicyRolloutService {

    private final TenantGuard tenantGuard;
    private final TenantRepository tenantRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;
    private final DevicePaymentMethodPolicyRepository devicePolicyRepository;
    private final PaymentMethodPolicyTemplateRepository templateRepository;
    private final PaymentMethodPolicyRolloutRepository rolloutRepository;
    private final TenantPaymentMethodService tenantPaymentMethodService;
    private final OperationalEventLogService operationalEventLogService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PaymentPolicyRolloutPreviewResponse preview(Long templateId, PaymentPolicyRolloutRequest req, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE, TenantUserRole.TENANT_CASHIER);
        TenantContext ctx = tenantGuard.requireContext();
        Tenant tenant = tenantRepository.findById(ctx.tenantId()).orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        PaymentMethodPolicyTemplate template = requireTemplateWithItems(ctx.tenantId(), templateId);
        ensureTemplateActive(template);
        UnidadeAtendimento unidade = requireUnidadeOfTenant(ctx.tenantId(), req.getUnidadeId());

        RolloutComputation comp = compute(ctx.tenantId(), template, unidade, req);

        PaymentPolicyRolloutPreviewResponse resp = new PaymentPolicyRolloutPreviewResponse();
        resp.setTemplateId(template.getId());
        resp.setUnidadeId(unidade.getId());
        resp.setRolloutMode(req.getRolloutMode());
        resp.setOverwriteMode(req.getOverwriteMode());
        resp.setTotalDevicesTargeted(comp.totalDevicesTargeted);
        resp.setTotalPoliciesToCreate(comp.totalPoliciesToCreate);
        resp.setTotalPoliciesToUpdate(comp.totalPoliciesToUpdate);
        resp.setTotalPoliciesToSkip(comp.totalPoliciesToSkip);
        resp.setWarnings(comp.warnings);
        resp.setDeviceResults(comp.deviceResults);

        operationalEventLogService.logPublicEvent(
                tenant,
                null,
                unidade,
                null,
                null,
                OperationalEventType.PAYMENT_POLICY_ROLLOUT_PREVIEWED,
                OperationalEntityType.PAYMENT_POLICY_TEMPLATE,
                template.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Preview de rollout de template de policy",
                Map.of(
                        "templateId", template.getId(),
                        "templateCode", template.getCode(),
                        "unidadeId", unidade.getId(),
                        "rolloutMode", req.getRolloutMode().name(),
                        "overwriteMode", req.getOverwriteMode().name(),
                        "targetDeviceType", comp.targetDeviceType != null ? comp.targetDeviceType.name() : null,
                        "totalDevicesTargeted", comp.totalDevicesTargeted,
                        "totalPoliciesToCreate", comp.totalPoliciesToCreate,
                        "totalPoliciesToUpdate", comp.totalPoliciesToUpdate,
                        "totalPoliciesToSkip", comp.totalPoliciesToSkip
                ),
                ip,
                userAgent
        );

        return resp;
    }

    public PaymentPolicyRolloutApplyResponse apply(Long templateId, PaymentPolicyRolloutRequest req, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = TenantContextHolder.require();
        try {
            return doApply(ctx.tenantId(), templateId, req, ip, userAgent, ctx.userId());
        } catch (RuntimeException e) {
            recordFailedRollout(ctx.tenantId(), templateId, req, ctx.userId(), e.getMessage());
            throw e;
        }
    }

    @Transactional
    protected PaymentPolicyRolloutApplyResponse doApply(Long tenantId, Long templateId, PaymentPolicyRolloutRequest req, String ip, String userAgent, Long actorUserId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        PaymentMethodPolicyTemplate template = requireTemplateWithItems(tenantId, templateId);
        ensureTemplateActive(template);
        UnidadeAtendimento unidade = requireUnidadeOfTenant(tenantId, req.getUnidadeId());

        tenantPaymentMethodService.ensureDefaultsForTenant(tenantId);
        RolloutComputation comp = compute(tenantId, template, unidade, req);

        PaymentMethodPolicyRollout rollout = new PaymentMethodPolicyRollout();
        rollout.setTenant(tenant);
        rollout.setTemplate(template);
        rollout.setUnidadeAtendimento(unidade);
        rollout.setTargetDeviceType(comp.targetDeviceType);
        rollout.setRolloutMode(req.getRolloutMode());
        rollout.setOverwriteMode(req.getOverwriteMode());
        rollout.setDryRun(false);
        rollout.setExecutionMode(PaymentMethodPolicyRolloutExecutionMode.SYNC);
        rollout.setStatus(PaymentMethodPolicyRolloutStatus.APPLIED);
        rollout.setRequestedAt(Instant.now());
        rollout.setCreatedBy(actorUserId);
        rollout.setTotalDevicesTargeted(comp.totalDevicesTargeted);
        rollout.setStartedAt(Instant.now());

        // Persist rollout primeiro para gerar ID e linkar nas policies
        rollout = rolloutRepository.save(rollout);

        int created = 0;
        int updated = 0;
        int skipped = 0;

        Instant appliedAt = Instant.now();
        for (DevicePlan plan : comp.devicePlans) {
            for (PaymentMethodPolicyTemplateItem item : comp.templateItems) {
                DevicePaymentMethodPolicy existing = plan.existingByMethod.get(item.getPaymentMethodCode());
                Action action = decideAction(existing, req.getOverwriteMode());
                if (action == Action.SKIP) {
                    skipped++;
                    continue;
                }
                if (existing == null) {
                    DevicePaymentMethodPolicy p = new DevicePaymentMethodPolicy();
                    p.setTenant(tenant);
                    p.setDispositivoOperacional(plan.device);
                    p.setUnidadeAtendimento(unidade);
                    p.setPaymentMethodCode(item.getPaymentMethodCode());
                    applyItemToPolicy(p, item);
                    p.setInheritFromUnidade(false);
                    p.setTemplateManaged(true);
                    p.setManualOverride(false);
                    p.setSourceTemplate(template);
                    p.setSourceRollout(rollout);
                    p.setTemplateAppliedAt(appliedAt);
                    p.setCreatedBy(actorUserId);
                    devicePolicyRepository.save(p);
                    created++;
                } else {
                    applyItemToPolicy(existing, item);
                    existing.setInheritFromUnidade(false);
                    existing.setTemplateManaged(true);
                    existing.setManualOverride(false);
                    existing.setSourceTemplate(template);
                    existing.setSourceRollout(rollout);
                    existing.setTemplateAppliedAt(appliedAt);
                    existing.setUpdatedBy(actorUserId);
                    devicePolicyRepository.save(existing);
                    updated++;
                }
            }
        }

        rollout.setTotalPoliciesCreated(created);
        rollout.setTotalPoliciesUpdated(updated);
        rollout.setTotalPoliciesSkipped(skipped);
        rollout.setTotalErrors(0);
        rollout.setFinishedAt(Instant.now());
        rollout.setLastProgressAt(Instant.now());
        rollout.setResultJson(writeJson(Map.of(
                "templateId", template.getId(),
                "templateCode", template.getCode(),
                "unidadeId", unidade.getId(),
                "rolloutMode", req.getRolloutMode().name(),
                "overwriteMode", req.getOverwriteMode().name(),
                "targetDeviceType", comp.targetDeviceType != null ? comp.targetDeviceType.name() : null,
                "totalDevicesTargeted", comp.totalDevicesTargeted,
                "totalPoliciesCreated", created,
                "totalPoliciesUpdated", updated,
                "totalPoliciesSkipped", skipped
        )));
        rolloutRepository.save(rollout);

        operationalEventLogService.logPublicEvent(
                tenant,
                null,
                unidade,
                null,
                null,
                OperationalEventType.PAYMENT_POLICY_ROLLOUT_APPLIED,
                OperationalEntityType.PAYMENT_POLICY_ROLLOUT,
                rollout.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Rollout de template de policy aplicado",
                Map.ofEntries(
                        Map.entry("rolloutId", rollout.getId()),
                        Map.entry("templateId", template.getId()),
                        Map.entry("templateCode", template.getCode()),
                        Map.entry("unidadeId", unidade.getId()),
                        Map.entry("rolloutMode", req.getRolloutMode().name()),
                        Map.entry("overwriteMode", req.getOverwriteMode().name()),
                        Map.entry("targetDeviceType", comp.targetDeviceType != null ? comp.targetDeviceType.name() : null),
                        Map.entry("totalDevicesTargeted", comp.totalDevicesTargeted),
                        Map.entry("totalPoliciesCreated", created),
                        Map.entry("totalPoliciesUpdated", updated),
                        Map.entry("totalPoliciesSkipped", skipped)
                ),
                ip,
                userAgent
        );

        PaymentPolicyRolloutApplyResponse resp = new PaymentPolicyRolloutApplyResponse();
        resp.setRolloutId(rollout.getId());
        resp.setTemplateId(template.getId());
        resp.setUnidadeId(unidade.getId());
        resp.setStatus(rollout.getStatus());
        resp.setTotalDevicesTargeted(comp.totalDevicesTargeted);
        resp.setTotalPoliciesCreated(created);
        resp.setTotalPoliciesUpdated(updated);
        resp.setTotalPoliciesSkipped(skipped);
        resp.setTotalErrors(0);
        resp.setStartedAt(rollout.getStartedAt());
        resp.setFinishedAt(rollout.getFinishedAt());
        resp.setDeviceResults(comp.deviceResults);
        return resp;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void recordFailedRollout(Long tenantId, Long templateId, PaymentPolicyRolloutRequest req, Long actorUserId, String errorMessage) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) return;
        UnidadeAtendimento unidade = unidadeAtendimentoRepository.findById(req.getUnidadeId()).orElse(null);

        PaymentMethodPolicyRollout failed = new PaymentMethodPolicyRollout();
        failed.setTenant(tenant);
        PaymentMethodPolicyTemplate template = templateRepository.findByIdAndTenant_Id(templateId, tenantId).orElse(null);
        if (template != null) failed.setTemplate(template);
        if (unidade != null) failed.setUnidadeAtendimento(unidade);
        failed.setTargetDeviceType(req.getTargetDeviceType());
        failed.setRolloutMode(req.getRolloutMode());
        failed.setOverwriteMode(req.getOverwriteMode());
        failed.setDryRun(false);
        failed.setStatus(PaymentMethodPolicyRolloutStatus.FAILED);
        failed.setCreatedBy(actorUserId);
        failed.setTotalErrors(1);
        failed.setResultJson(writeJson(Map.of(
                "error", errorMessage,
                "templateId", templateId,
                "unidadeId", req.getUnidadeId(),
                "rolloutMode", req.getRolloutMode() != null ? req.getRolloutMode().name() : null,
                "overwriteMode", req.getOverwriteMode() != null ? req.getOverwriteMode().name() : null
        )));
        rolloutRepository.save(failed);

        operationalEventLogService.logPublicEvent(
                tenant,
                null,
                unidade,
                null,
                null,
                OperationalEventType.PAYMENT_POLICY_ROLLOUT_FAILED,
                OperationalEntityType.PAYMENT_POLICY_ROLLOUT,
                failed.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Rollout de template de policy falhou",
                Map.of(
                        "rolloutId", failed.getId(),
                        "templateId", templateId,
                        "unidadeId", req.getUnidadeId(),
                        "rolloutMode", req.getRolloutMode() != null ? req.getRolloutMode().name() : null,
                        "overwriteMode", req.getOverwriteMode() != null ? req.getOverwriteMode().name() : null
                ),
                null,
                null
        );
    }

    private PaymentMethodPolicyTemplate requireTemplateWithItems(Long tenantId, Long templateId) {
        return templateRepository.findWithItemsByIdAndTenant_Id(templateId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Template não encontrado."));
    }

    private void ensureTemplateActive(PaymentMethodPolicyTemplate template) {
        if (template.getStatus() != PaymentMethodPolicyTemplateStatus.ACTIVE) {
            throw new BusinessException("Template não pode ser aplicado/preview: status " + template.getStatus().name());
        }
    }

    private UnidadeAtendimento requireUnidadeOfTenant(Long tenantId, Long unidadeId) {
        UnidadeAtendimento u = unidadeAtendimentoRepository.findById(unidadeId)
                .orElseThrow(() -> new ResourceNotFoundException("Unidade não encontrada."));
        Long tId = u.getInstituicao() != null && u.getInstituicao().getTenant() != null ? u.getInstituicao().getTenant().getId() : null;
        if (!Objects.equals(tId, tenantId)) throw new ResourceNotFoundException("Unidade não encontrada.");
        return u;
    }

    private RolloutComputation compute(Long tenantId, PaymentMethodPolicyTemplate template, UnidadeAtendimento unidade, PaymentPolicyRolloutRequest req) {
        List<DispositivoOperacional> devices = resolveTargetDevices(tenantId, unidade.getId(), template, req);
        if (devices.isEmpty()) throw new BusinessException("Nenhum device alvo encontrado para rollout.");

        List<Long> deviceIds = devices.stream().map(DispositivoOperacional::getId).toList();
        List<DevicePaymentMethodPolicy> existing = devicePolicyRepository.findByTenantAndUnidadeAndDeviceIds(tenantId, unidade.getId(), deviceIds);

        Map<Long, Map<PaymentMethodCode, DevicePaymentMethodPolicy>> existingByDevice = new HashMap<>();
        for (DevicePaymentMethodPolicy p : existing) {
            existingByDevice.computeIfAbsent(p.getDispositivoOperacional().getId(), k -> new EnumMap<>(PaymentMethodCode.class))
                    .put(p.getPaymentMethodCode(), p);
        }

        List<PaymentMethodPolicyTemplateItem> templateItems = template.getItems() != null ? template.getItems() : List.of();
        if (templateItems.isEmpty()) throw new BusinessException("Template não possui itens.");

        RolloutComputation comp = new RolloutComputation();
        comp.targetDeviceType = resolveEffectiveTargetType(template, req);
        comp.templateItems = templateItems;
        comp.totalDevicesTargeted = devices.size();

        for (DispositivoOperacional d : devices) {
            DevicePlan plan = new DevicePlan(d);
            plan.existingByMethod = existingByDevice.getOrDefault(d.getId(), new EnumMap<>(PaymentMethodCode.class));
            comp.devicePlans.add(plan);

            DevicePolicyRolloutResultResponse dr = new DevicePolicyRolloutResultResponse();
            dr.setDeviceId(d.getId());
            dr.setDeviceName(d.getNome());
            dr.setDeviceType(d.getOperationalDeviceType());

            List<String> toCreate = new ArrayList<>();
            List<String> toUpdate = new ArrayList<>();
            List<String> skipped = new ArrayList<>();

            for (PaymentMethodPolicyTemplateItem item : templateItems) {
                DevicePaymentMethodPolicy ex = plan.existingByMethod.get(item.getPaymentMethodCode());
                Action action = decideAction(ex, req.getOverwriteMode());
                if (action == Action.CREATE) {
                    toCreate.add(item.getPaymentMethodCode().name());
                } else if (action == Action.UPDATE) {
                    toUpdate.add(item.getPaymentMethodCode().name());
                } else {
                    skipped.add(item.getPaymentMethodCode().name());
                }
            }

            dr.setPoliciesToCreate(toCreate);
            dr.setPoliciesToUpdate(toUpdate);
            dr.setPoliciesSkipped(skipped);
            dr.setWarnings(List.of());
            dr.setErrors(List.of());
            comp.deviceResults.add(dr);

            comp.totalPoliciesToCreate += toCreate.size();
            comp.totalPoliciesToUpdate += toUpdate.size();
            comp.totalPoliciesToSkip += skipped.size();
        }

        return comp;
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

    private void applyItemToPolicy(DevicePaymentMethodPolicy policy, PaymentMethodPolicyTemplateItem item) {
        // Validações de compatibilidade por método (sem alterar callback/polling)
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

    private Action decideAction(DevicePaymentMethodPolicy existing, PaymentMethodPolicyOverwriteMode overwriteMode) {
        if (existing == null) return Action.CREATE;
        if (overwriteMode == PaymentMethodPolicyOverwriteMode.OVERWRITE_EXISTING) return Action.UPDATE;
        if (overwriteMode == PaymentMethodPolicyOverwriteMode.SKIP_EXISTING) return Action.SKIP;
        if (overwriteMode == PaymentMethodPolicyOverwriteMode.OVERWRITE_ONLY_TEMPLATE_MANAGED) {
            return (existing.isTemplateManaged() && !existing.isManualOverride()) ? Action.UPDATE : Action.SKIP;
        }
        return Action.SKIP;
    }

    private String writeJson(Map<String, Object> json) {
        if (json == null) return null;
        try {
            return objectMapper.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    enum Action { CREATE, UPDATE, SKIP }

    static class DevicePlan {
        final DispositivoOperacional device;
        Map<PaymentMethodCode, DevicePaymentMethodPolicy> existingByMethod = new EnumMap<>(PaymentMethodCode.class);
        DevicePlan(DispositivoOperacional device) { this.device = device; }
    }

    static class RolloutComputation {
        OperationalDeviceType targetDeviceType;
        List<PaymentMethodPolicyTemplateItem> templateItems;
        int totalDevicesTargeted;
        int totalPoliciesToCreate;
        int totalPoliciesToUpdate;
        int totalPoliciesToSkip;
        final List<String> warnings = new ArrayList<>();
        final List<DevicePlan> devicePlans = new ArrayList<>();
        final List<DevicePolicyRolloutResultResponse> deviceResults = new ArrayList<>();
    }
}
