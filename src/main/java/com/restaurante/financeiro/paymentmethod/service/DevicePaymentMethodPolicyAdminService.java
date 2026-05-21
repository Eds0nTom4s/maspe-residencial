package com.restaurante.financeiro.paymentmethod.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.UpdateDevicePaymentMethodPolicyRequest;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.financeiro.paymentmethod.entity.DevicePaymentMethodPolicy;
import com.restaurante.financeiro.paymentmethod.entity.TenantPaymentMethod;
import com.restaurante.financeiro.paymentmethod.repository.DevicePaymentMethodPolicyRepository;
import com.restaurante.financeiro.paymentmethod.repository.TenantPaymentMethodRepository;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.enums.*;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DevicePaymentMethodPolicyAdminService {

    private final TenantGuard tenantGuard;
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;
    private final DevicePaymentMethodPolicyRepository policyRepository;
    private final TenantPaymentMethodRepository tenantPaymentMethodRepository;
    private final TenantPaymentMethodService tenantPaymentMethodService;
    private final OperationalEventLogService operationalEventLogService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<DevicePaymentMethodPolicy> listPolicies(Long deviceId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE, TenantUserRole.TENANT_CASHIER);
        TenantContext ctx = tenantGuard.requireContext();
        ensureDevice(ctx.tenantId(), deviceId);
        return policyRepository.findByTenant_IdAndDispositivoOperacional_Id(ctx.tenantId(), deviceId);
    }

    @Transactional(readOnly = true)
    public DevicePaymentMethodPolicy getPolicy(Long deviceId, PaymentMethodCode code) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE, TenantUserRole.TENANT_CASHIER);
        TenantContext ctx = tenantGuard.requireContext();
        ensureDevice(ctx.tenantId(), deviceId);
        return policyRepository.findByTenant_IdAndDispositivoOperacional_IdAndPaymentMethodCode(ctx.tenantId(), deviceId, code)
                .orElseThrow(() -> new ResourceNotFoundException("Política não encontrada."));
    }

    @Transactional
    public DevicePaymentMethodPolicy upsert(Long deviceId, PaymentMethodCode code, UpdateDevicePaymentMethodPolicyRequest req, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = TenantContextHolder.require();

        DispositivoOperacional device = ensureDevice(ctx.tenantId(), deviceId);
        tenantPaymentMethodService.ensureDefaultsForTenant(ctx.tenantId());
        TenantPaymentMethod tenantMethod = tenantPaymentMethodRepository.findByTenantIdAndCode(ctx.tenantId(), code)
                .orElseThrow(() -> new ResourceNotFoundException("Método não configurado no tenant."));

        DevicePaymentMethodPolicy policy = policyRepository.findByTenant_IdAndDispositivoOperacional_IdAndPaymentMethodCode(ctx.tenantId(), deviceId, code)
                .orElseGet(() -> {
                    DevicePaymentMethodPolicy p = new DevicePaymentMethodPolicy();
                    p.setTenant(tenantMethod.getTenant());
                    p.setDispositivoOperacional(device);
                    p.setUnidadeAtendimento(device.getUnidadeAtendimento());
                    p.setPaymentMethodCode(code);
                    p.setStatus(PaymentMethodPolicyStatus.INHERIT);
                    p.setInheritFromUnidade(true);
                    p.setCreatedBy(ctx.userId());
                    return p;
                });

        PaymentMethodPolicyStatus anterior = policy.getStatus();

        // Se havia sido gerenciado por template, qualquer ajuste via endpoint manual passa a ser override manual
        policy.setManualOverride(true);
        if (policy.isTemplateManaged()) {
            policy.setTemplateManaged(false);
            policy.setSourceTemplate(null);
            policy.setSourceRollout(null);
            policy.setTemplateAppliedAt(null);
        }

        if (req.getInheritFromUnidade() != null) policy.setInheritFromUnidade(req.getInheritFromUnidade());
        boolean inherit = policy.isInheritFromUnidade();
        if (inherit) {
            policy.setStatus(PaymentMethodPolicyStatus.INHERIT);
        } else if (req.getStatus() != null) {
            policy.setStatus(req.getStatus());
        } else if (policy.getStatus() == null || policy.getStatus() == PaymentMethodPolicyStatus.INHERIT) {
            policy.setStatus(PaymentMethodPolicyStatus.ALLOW);
        }

        if (req.getEnabledForPos() != null) policy.setEnabledForPos(req.getEnabledForPos());
        if (req.getEnabledForPedido() != null) policy.setEnabledForPedido(req.getEnabledForPedido());
        if (req.getEnabledForFundoConsumo() != null) policy.setEnabledForFundoConsumo(req.getEnabledForFundoConsumo());
        if (req.getCanConfirmManual() != null) policy.setCanConfirmManual(req.getCanConfirmManual());
        if (req.getCanStartGateway() != null) policy.setCanStartGateway(req.getCanStartGateway());
        if (req.getMinAmount() != null) policy.setMinAmount(req.getMinAmount());
        if (req.getMaxAmount() != null) policy.setMaxAmount(req.getMaxAmount());
        if (req.getOverrideReason() != null) policy.setOverrideReason(req.getOverrideReason());
        if (req.getMetadata() != null) policy.setMetadataJson(writeMetadata(req.getMetadata()));
        policy.setUpdatedBy(ctx.userId());

        validateMinMax(policy.getMinAmount(), policy.getMaxAmount());
        validatePolicyCompatibility(tenantMethod, policy);

        DevicePaymentMethodPolicy saved = policyRepository.save(policy);

        operationalEventLogService.logPublicEvent(
                tenantMethod.getTenant(), null, device.getUnidadeAtendimento(), null, null,
                OperationalEventType.PAYMENT_METHOD_DEVICE_POLICY_UPDATED,
                OperationalEntityType.DEVICE_PAYMENT_METHOD_POLICY,
                saved.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Política de método de pagamento (device) atualizada",
                Map.ofEntries(
                        Map.entry("deviceId", deviceId),
                        Map.entry("unidadeId", device.getUnidadeAtendimento() != null ? device.getUnidadeAtendimento().getId() : null),
                        Map.entry("code", code.name()),
                        Map.entry("statusAnterior", anterior != null ? anterior.name() : null),
                        Map.entry("statusNovo", saved.getStatus() != null ? saved.getStatus().name() : null),
                        Map.entry("inheritFromUnidade", saved.isInheritFromUnidade()),
                        Map.entry("enabledForPos", saved.getEnabledForPos()),
                        Map.entry("enabledForPedido", saved.getEnabledForPedido()),
                        Map.entry("enabledForFundoConsumo", saved.getEnabledForFundoConsumo()),
                        Map.entry("canConfirmManual", saved.getCanConfirmManual()),
                        Map.entry("canStartGateway", saved.getCanStartGateway()),
                        Map.entry("minAmount", saved.getMinAmount()),
                        Map.entry("maxAmount", saved.getMaxAmount())
                ),
                ip, userAgent
        );

        return saved;
    }

    @Transactional
    public void remove(Long deviceId, PaymentMethodCode code, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = TenantContextHolder.require();
        ensureDevice(ctx.tenantId(), deviceId);
        DevicePaymentMethodPolicy policy = policyRepository.findByTenant_IdAndDispositivoOperacional_IdAndPaymentMethodCode(ctx.tenantId(), deviceId, code)
                .orElseThrow(() -> new ResourceNotFoundException("Política não encontrada."));
        policyRepository.delete(policy);
        TenantPaymentMethod tm = tenantPaymentMethodService.getOrThrow(ctx.tenantId(), code);
        operationalEventLogService.logPublicEvent(
                tm.getTenant(), null, policy.getUnidadeAtendimento(), null, null,
                OperationalEventType.PAYMENT_METHOD_DEVICE_POLICY_REMOVED,
                OperationalEntityType.DEVICE_PAYMENT_METHOD_POLICY,
                policy.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Política de método de pagamento (device) removida",
                Map.of("deviceId", deviceId, "code", code.name()),
                ip, userAgent
        );
    }

    private DispositivoOperacional ensureDevice(Long tenantId, Long deviceId) {
        return dispositivoOperacionalRepository.findByIdAndTenantId(deviceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Device não encontrado."));
    }

    private String writeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new BusinessException("Metadata inválida.");
        }
    }

    public Map<String, Object> readMetadata(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private void validatePolicyCompatibility(TenantPaymentMethod tenantMethod, DevicePaymentMethodPolicy policy) {
        if (!policy.isInheritFromUnidade()) {
            if (policy.getEnabledForPos() != null && policy.getEnabledForPos() && !tenantMethod.isEnabledForPos()) {
                throw new BusinessException("Política inválida: tenant bloqueia POS.");
            }
            if (policy.getEnabledForPedido() != null && policy.getEnabledForPedido() && !tenantMethod.isEnabledForPedido()) {
                throw new BusinessException("Política inválida: tenant bloqueia PEDIDO.");
            }
            if (policy.getEnabledForFundoConsumo() != null && policy.getEnabledForFundoConsumo() && !tenantMethod.isEnabledForFundoConsumo()) {
                throw new BusinessException("Política inválida: tenant bloqueia FUNDO_CONSUMO.");
            }
        }
    }

    private void validateMinMax(BigDecimal min, BigDecimal max) {
        if (min != null && min.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException("minAmount não pode ser negativo.");
        if (max != null && max.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException("maxAmount não pode ser negativo.");
        if (min != null && max != null && max.compareTo(min) < 0) throw new BusinessException("maxAmount deve ser >= minAmount.");
    }
}
