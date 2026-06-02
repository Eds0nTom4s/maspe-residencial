package com.restaurante.device.capability.template.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.device.capability.entity.DeviceOperationalCapabilityEntity;
import com.restaurante.device.capability.repository.DeviceOperationalCapabilityRepository;
import com.restaurante.device.capability.template.entity.DeviceCapabilityRollout;
import com.restaurante.device.capability.template.entity.DeviceCapabilityTemplate;
import com.restaurante.device.capability.template.entity.DeviceCapabilityTemplateItem;
import com.restaurante.device.capability.template.repository.DeviceCapabilityRolloutRepository;
import com.restaurante.device.capability.template.repository.DeviceCapabilityTemplateItemRepository;
import com.restaurante.device.capability.template.repository.DeviceCapabilityTemplateRepository;
import com.restaurante.dto.request.DeviceCapabilityRolloutRequest;
import com.restaurante.dto.response.DeviceCapabilityRolloutApplyResponse;
import com.restaurante.dto.response.DeviceCapabilityRolloutPreviewResponse;
import com.restaurante.dto.response.DeviceCapabilityRolloutResultResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.DeviceCapabilityOverwriteMode;
import com.restaurante.model.enums.DeviceCapabilityRolloutMode;
import com.restaurante.model.enums.DeviceCapabilityRolloutStatus;
import com.restaurante.model.enums.DeviceCapabilityTemplateStatus;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeviceCapabilityRolloutService {

    private final TenantGuard tenantGuard;
    private final DeviceCapabilityTemplateRepository templateRepository;
    private final DeviceCapabilityTemplateItemRepository itemRepository;
    private final DeviceCapabilityRolloutRepository rolloutRepository;
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final DeviceOperationalCapabilityRepository deviceCapabilityRepository;
    private final OperationalEventLogService operationalEventLogService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public DeviceCapabilityRolloutPreviewResponse preview(Long templateId, DeviceCapabilityRolloutRequest req, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE, TenantUserRole.TENANT_CASHIER);
        TenantContext ctx = tenantGuard.requireContext();
        DeviceCapabilityTemplate tpl = templateRepository.findByIdAndTenant_Id(templateId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("DeviceCapabilityTemplate", "id", templateId));
        if (tpl.getStatus() != DeviceCapabilityTemplateStatus.ACTIVE) throw new BusinessException("TEMPLATE_INACTIVE");

        UnidadeAtendimento ua = unidadeAtendimentoRepository.findById(req.getUnidadeId())
                .orElseThrow(() -> new ResourceNotFoundException("UnidadeAtendimento", "id", req.getUnidadeId()));
        if (ua.getInstituicao() == null || ua.getInstituicao().getTenant() == null || !ua.getInstituicao().getTenant().getId().equals(ctx.tenantId())) {
            throw new ResourceNotFoundException("UnidadeAtendimento", "id", req.getUnidadeId());
        }

        List<DeviceCapabilityTemplateItem> items = itemRepository.listByTenantAndTemplate(ctx.tenantId(), templateId);
        List<DispositivoOperacional> devices = resolveTargetDevices(ctx.tenantId(), req);

        RolloutCalc calc = calculate(devices, items, req.getOverwriteMode());

        DeviceCapabilityRolloutPreviewResponse resp = new DeviceCapabilityRolloutPreviewResponse();
        resp.setTemplateId(templateId);
        resp.setUnidadeId(req.getUnidadeId());
        resp.setRolloutMode(req.getRolloutMode());
        resp.setOverwriteMode(req.getOverwriteMode());
        resp.setTotalDevicesTargeted(devices.size());
        resp.setTotalCapabilitiesToCreate(calc.totalCreate);
        resp.setTotalCapabilitiesToUpdate(calc.totalUpdate);
        resp.setTotalCapabilitiesToSkip(calc.totalSkip);
        resp.setWarnings(calc.warnings);
        resp.setDeviceResults(calc.deviceResults);

        operationalEventLogService.logGenericRequiresNew(
                OperationalEventType.DEVICE_CAPABILITY_ROLLOUT_PREVIEWED,
                OperationalEntityType.DEVICE_CAPABILITY,
                0L,
                OperationalOrigem.SYSTEM,
                "Device capability rollout previewed",
                rolloutPreviewMetadata(ctx, tpl, req, devices.size(), calc),
                ip,
                userAgent
        );

        return resp;
    }

    @Transactional
    public DeviceCapabilityRolloutApplyResponse apply(Long templateId, DeviceCapabilityRolloutRequest req, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
        TenantContext ctx = tenantGuard.requireContext();
        DeviceCapabilityTemplate tpl = templateRepository.findByIdAndTenant_Id(templateId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("DeviceCapabilityTemplate", "id", templateId));
        if (tpl.getStatus() != DeviceCapabilityTemplateStatus.ACTIVE) throw new BusinessException("TEMPLATE_INACTIVE");

        UnidadeAtendimento ua = unidadeAtendimentoRepository.findById(req.getUnidadeId())
                .orElseThrow(() -> new ResourceNotFoundException("UnidadeAtendimento", "id", req.getUnidadeId()));
        if (ua.getInstituicao() == null || ua.getInstituicao().getTenant() == null || !ua.getInstituicao().getTenant().getId().equals(ctx.tenantId())) {
            throw new ResourceNotFoundException("UnidadeAtendimento", "id", req.getUnidadeId());
        }

        List<DeviceCapabilityTemplateItem> items = itemRepository.listByTenantAndTemplate(ctx.tenantId(), templateId);
        List<DispositivoOperacional> devices = resolveTargetDevices(ctx.tenantId(), req);

        DeviceCapabilityRollout rollout = new DeviceCapabilityRollout();
        rollout.setTenant(ua.getInstituicao().getTenant());
        rollout.setTemplate(tpl);
        rollout.setUnidadeAtendimento(ua);
        rollout.setRolloutMode(req.getRolloutMode());
        rollout.setOverwriteMode(req.getOverwriteMode());
        rollout.setTargetDeviceType(req.getTargetDeviceType());
        rollout.setDryRun(false);
        rollout.setStatus(DeviceCapabilityRolloutStatus.APPLIED);
        rollout.setCreatedBy(ctx.userId());
        rolloutRepository.save(rollout);

        RolloutApplyResult result = applyToDevices(rollout, devices, items, req.getOverwriteMode());

        rollout.setTotalDevicesTargeted(devices.size());
        rollout.setTotalCapabilitiesCreated(result.totalCreated);
        rollout.setTotalCapabilitiesUpdated(result.totalUpdated);
        rollout.setTotalCapabilitiesSkipped(result.totalSkipped);
        rollout.setTotalErrors(result.totalErrors);
        rollout.setFinishedAt(Instant.now());
        try {
            rollout.setResultJson(objectMapper.writeValueAsString(result.deviceResults));
        } catch (Exception ignored) { }
        rolloutRepository.save(rollout);

        operationalEventLogService.logGeneric(
                OperationalEventType.DEVICE_CAPABILITY_ROLLOUT_APPLIED,
                OperationalEntityType.DEVICE_CAPABILITY,
                rollout.getId(),
                OperationalOrigem.SYSTEM,
                "Device capability rollout applied",
                rolloutApplyMetadata(ctx, rollout, tpl, req, devices.size(), result),
                ip,
                userAgent
        );

        DeviceCapabilityRolloutApplyResponse resp = new DeviceCapabilityRolloutApplyResponse();
        resp.setRolloutId(rollout.getId());
        resp.setTemplateId(tpl.getId());
        resp.setUnidadeId(req.getUnidadeId());
        resp.setStatus(rollout.getStatus());
        resp.setTotalDevicesTargeted(devices.size());
        resp.setTotalCapabilitiesCreated(result.totalCreated);
        resp.setTotalCapabilitiesUpdated(result.totalUpdated);
        resp.setTotalCapabilitiesSkipped(result.totalSkipped);
        resp.setTotalErrors(result.totalErrors);
        resp.setStartedAt(rollout.getStartedAt());
        resp.setFinishedAt(rollout.getFinishedAt());
        resp.setDeviceResults(result.deviceResults);
        return resp;
    }

    private List<DispositivoOperacional> resolveTargetDevices(Long tenantId, DeviceCapabilityRolloutRequest req) {
        if (req.getRolloutMode() == DeviceCapabilityRolloutMode.UNIT_ALL_DEVICES) {
            return dispositivoOperacionalRepository.searchByTenantAndFilters(tenantId, null, null, req.getUnidadeId(), null, org.springframework.data.domain.Pageable.unpaged()).getContent();
        }
        if (req.getRolloutMode() == DeviceCapabilityRolloutMode.UNIT_BY_DEVICE_TYPE) {
            if (req.getTargetDeviceType() == null) throw new BusinessException("TARGET_DEVICE_TYPE_REQUIRED");
            return dispositivoOperacionalRepository.findByTenantAndUnidadeAtendimentoAndOperationalType(tenantId, req.getUnidadeId(), req.getTargetDeviceType());
        }
        if (req.getRolloutMode() == DeviceCapabilityRolloutMode.SELECTED_DEVICES) {
            if (req.getSelectedDeviceIds() == null || req.getSelectedDeviceIds().isEmpty()) throw new BusinessException("SELECTED_DEVICE_IDS_REQUIRED");
            List<DispositivoOperacional> found = dispositivoOperacionalRepository.findByTenantAndUnidadeAtendimentoAndIds(tenantId, req.getUnidadeId(), req.getSelectedDeviceIds());
            if (found.size() != req.getSelectedDeviceIds().size()) {
                throw new BusinessException("SELECTED_DEVICES_INVALID");
            }
            return found;
        }
        throw new BusinessException("ROLLOUT_MODE_INVALID");
    }

    private RolloutCalc calculate(List<DispositivoOperacional> devices, List<DeviceCapabilityTemplateItem> items, DeviceCapabilityOverwriteMode overwriteMode) {
        RolloutCalc calc = new RolloutCalc();
        for (DispositivoOperacional d : devices) {
            DeviceCapabilityRolloutResultResponse dr = new DeviceCapabilityRolloutResultResponse();
            dr.setDeviceId(d.getId());
            dr.setDeviceName(d.getNome());
            dr.setDeviceType(d.getOperationalDeviceType());
            dr.setCapabilitiesToCreate(new ArrayList<>());
            dr.setCapabilitiesToUpdate(new ArrayList<>());
            dr.setCapabilitiesSkipped(new ArrayList<>());
            dr.setWarnings(new ArrayList<>());
            dr.setErrors(new ArrayList<>());

            for (DeviceCapabilityTemplateItem it : items) {
                DeviceOperationalCapabilityEntity existing = deviceCapabilityRepository
                        .findByTenant_IdAndDispositivoOperacional_IdAndCapability(d.getTenant().getId(), d.getId(), it.getCapability())
                        .orElse(null);
                if (existing == null) {
                    dr.getCapabilitiesToCreate().add(it.getCapability().name());
                    calc.totalCreate++;
                } else if (shouldSkip(existing, overwriteMode)) {
                    dr.getCapabilitiesSkipped().add(it.getCapability().name());
                    calc.totalSkip++;
                } else {
                    dr.getCapabilitiesToUpdate().add(it.getCapability().name());
                    calc.totalUpdate++;
                }
            }
            calc.deviceResults.add(dr);
        }
        return calc;
    }

    private RolloutApplyResult applyToDevices(DeviceCapabilityRollout rollout,
                                              List<DispositivoOperacional> devices,
                                              List<DeviceCapabilityTemplateItem> items,
                                              DeviceCapabilityOverwriteMode overwriteMode) {
        RolloutApplyResult out = new RolloutApplyResult();
        for (DispositivoOperacional d : devices) {
            DeviceCapabilityRolloutResultResponse dr = new DeviceCapabilityRolloutResultResponse();
            dr.setDeviceId(d.getId());
            dr.setDeviceName(d.getNome());
            dr.setDeviceType(d.getOperationalDeviceType());
            dr.setCapabilitiesToCreate(new ArrayList<>());
            dr.setCapabilitiesToUpdate(new ArrayList<>());
            dr.setCapabilitiesSkipped(new ArrayList<>());
            dr.setWarnings(new ArrayList<>());
            dr.setErrors(new ArrayList<>());

            for (DeviceCapabilityTemplateItem it : items) {
                DeviceOperationalCapabilityEntity existing = deviceCapabilityRepository
                        .findByTenant_IdAndDispositivoOperacional_IdAndCapability(d.getTenant().getId(), d.getId(), it.getCapability())
                        .orElse(null);
                if (existing == null) {
                    DeviceOperationalCapabilityEntity e = new DeviceOperationalCapabilityEntity();
                    e.setTenant(d.getTenant());
                    e.setDispositivoOperacional(d);
                    e.setCapability(it.getCapability());
                    e.setEnabled(it.isEnabled());
                    e.setSource("TEMPLATE");
                    e.setSourceTemplateId(rollout.getTemplate().getId());
                    e.setSourceRolloutId(rollout.getId());
                    e.setTemplateManaged(true);
                    e.setManualOverride(false);
                    e.setTemplateAppliedAt(Instant.now());
                    deviceCapabilityRepository.save(e);
                    dr.getCapabilitiesToCreate().add(it.getCapability().name());
                    out.totalCreated++;
                } else if (shouldSkip(existing, overwriteMode)) {
                    dr.getCapabilitiesSkipped().add(it.getCapability().name());
                    out.totalSkipped++;
                } else {
                    existing.setEnabled(it.isEnabled());
                    existing.setSource("TEMPLATE");
                    existing.setSourceTemplateId(rollout.getTemplate().getId());
                    existing.setSourceRolloutId(rollout.getId());
                    existing.setTemplateManaged(true);
                    existing.setManualOverride(false);
                    existing.setTemplateAppliedAt(Instant.now());
                    deviceCapabilityRepository.save(existing);
                    dr.getCapabilitiesToUpdate().add(it.getCapability().name());
                    out.totalUpdated++;
                }
            }
            out.deviceResults.add(dr);
        }
        return out;
    }

    private boolean shouldSkip(DeviceOperationalCapabilityEntity existing, DeviceCapabilityOverwriteMode mode) {
        if (mode == DeviceCapabilityOverwriteMode.SKIP_EXISTING) return true;
        if (mode == DeviceCapabilityOverwriteMode.OVERWRITE_EXISTING) return false;
        if (mode == DeviceCapabilityOverwriteMode.OVERWRITE_ONLY_TEMPLATE_MANAGED) {
            return !(existing.isTemplateManaged() && !existing.isManualOverride());
        }
        return true;
    }

    private static class RolloutCalc {
        int totalCreate = 0;
        int totalUpdate = 0;
        int totalSkip = 0;
        List<String> warnings = new ArrayList<>();
        List<DeviceCapabilityRolloutResultResponse> deviceResults = new ArrayList<>();
    }

    private static class RolloutApplyResult {
        int totalCreated = 0;
        int totalUpdated = 0;
        int totalSkipped = 0;
        int totalErrors = 0;
        List<DeviceCapabilityRolloutResultResponse> deviceResults = new ArrayList<>();
    }

    private Map<String, Object> rolloutPreviewMetadata(TenantContext ctx,
                                                       DeviceCapabilityTemplate tpl,
                                                       DeviceCapabilityRolloutRequest req,
                                                       int totalDevices,
                                                       RolloutCalc calc) {
        Map<String, Object> m = new HashMap<>();
        m.put("tenantId", ctx.tenantId());
        m.put("templateId", tpl.getId());
        m.put("templateCode", tpl.getCode());
        m.put("unidadeId", req.getUnidadeId());
        m.put("rolloutMode", req.getRolloutMode().name());
        m.put("overwriteMode", req.getOverwriteMode().name());
        m.put("totalDevicesTargeted", totalDevices);
        m.put("totalCreate", calc.totalCreate);
        m.put("totalUpdate", calc.totalUpdate);
        m.put("totalSkip", calc.totalSkip);
        return m;
    }

    private Map<String, Object> rolloutApplyMetadata(TenantContext ctx,
                                                     DeviceCapabilityRollout rollout,
                                                     DeviceCapabilityTemplate tpl,
                                                     DeviceCapabilityRolloutRequest req,
                                                     int totalDevices,
                                                     RolloutApplyResult result) {
        Map<String, Object> m = new HashMap<>();
        m.put("tenantId", ctx.tenantId());
        m.put("rolloutId", rollout.getId());
        m.put("templateId", tpl.getId());
        m.put("templateCode", tpl.getCode());
        m.put("unidadeId", req.getUnidadeId());
        m.put("rolloutMode", req.getRolloutMode().name());
        m.put("overwriteMode", req.getOverwriteMode().name());
        m.put("totalDevicesTargeted", totalDevices);
        m.put("totalCapabilitiesCreated", result.totalCreated);
        m.put("totalCapabilitiesUpdated", result.totalUpdated);
        m.put("totalCapabilitiesSkipped", result.totalSkipped);
        m.put("totalErrors", result.totalErrors);
        return m;
    }
}
