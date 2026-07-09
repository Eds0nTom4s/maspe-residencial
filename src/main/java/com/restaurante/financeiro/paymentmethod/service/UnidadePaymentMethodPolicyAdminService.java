package com.restaurante.financeiro.paymentmethod.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.UpdateUnidadePaymentMethodPolicyRequest;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.financeiro.paymentmethod.entity.TenantPaymentMethod;
import com.restaurante.financeiro.paymentmethod.entity.UnidadePaymentMethodPolicy;
import com.restaurante.financeiro.paymentmethod.repository.TenantPaymentMethodRepository;
import com.restaurante.financeiro.paymentmethod.repository.UnidadePaymentMethodPolicyRepository;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.*;
import com.restaurante.repository.UnidadeAtendimentoRepository;
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
public class UnidadePaymentMethodPolicyAdminService {

    private final TenantGuard tenantGuard;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final UnidadePaymentMethodPolicyRepository policyRepository;
    private final TenantPaymentMethodRepository tenantPaymentMethodRepository;
    private final TenantPaymentMethodService tenantPaymentMethodService;
    private final OperationalEventLogService operationalEventLogService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<UnidadePaymentMethodPolicy> listPolicies(Long unidadeId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE, TenantUserRole.TENANT_CASHIER);
        TenantContext ctx = tenantGuard.requireContext();
        ensureUnidade(ctx.tenantId(), unidadeId);
        return policyRepository.findByTenant_IdAndUnidadeAtendimento_Id(ctx.tenantId(), unidadeId);
    }

    @Transactional(readOnly = true)
    public UnidadePaymentMethodPolicy getPolicy(Long unidadeId, PaymentMethodCode code) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE, TenantUserRole.TENANT_CASHIER);
        TenantContext ctx = tenantGuard.requireContext();
        ensureUnidade(ctx.tenantId(), unidadeId);
        return policyRepository.findByTenant_IdAndUnidadeAtendimento_IdAndPaymentMethodCode(ctx.tenantId(), unidadeId, code)
                .orElseThrow(() -> new ResourceNotFoundException("Política não encontrada."));
    }

    @Transactional
    public UnidadePaymentMethodPolicy upsert(Long unidadeId, PaymentMethodCode code, UpdateUnidadePaymentMethodPolicyRequest req, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = TenantContextHolder.require();

        UnidadeAtendimento unidade = ensureUnidade(ctx.tenantId(), unidadeId);
        tenantPaymentMethodService.ensureDefaultsForTenant(ctx.tenantId());
        TenantPaymentMethod tenantMethod = tenantPaymentMethodRepository.findByTenantIdAndCode(ctx.tenantId(), code)
                .orElseThrow(() -> new ResourceNotFoundException("Método não configurado no tenant."));

        UnidadePaymentMethodPolicy policy = policyRepository.findByTenant_IdAndUnidadeAtendimento_IdAndPaymentMethodCode(ctx.tenantId(), unidadeId, code)
                .orElseGet(() -> {
                    UnidadePaymentMethodPolicy p = new UnidadePaymentMethodPolicy();
                    p.setTenant(tenantMethod.getTenant());
                    p.setUnidadeAtendimento(unidade);
                    p.setPaymentMethodCode(code);
                    p.setStatus(PaymentMethodPolicyStatus.INHERIT);
                    p.setInheritFromTenant(true);
                    p.setCreatedBy(ctx.userId());
                    return p;
                });

        PaymentMethodPolicyStatus anterior = policy.getStatus();

        if (req.getInheritFromTenant() != null) policy.setInheritFromTenant(req.getInheritFromTenant());
        boolean inherit = policy.isInheritFromTenant();
        if (inherit) {
            policy.setStatus(PaymentMethodPolicyStatus.INHERIT);
        } else if (req.getStatus() != null) {
            policy.setStatus(req.getStatus());
        } else if (policy.getStatus() == null || policy.getStatus() == PaymentMethodPolicyStatus.INHERIT) {
            policy.setStatus(PaymentMethodPolicyStatus.ALLOW);
        }

        if (req.getEnabledForQr() != null) policy.setEnabledForQr(req.getEnabledForQr());
        if (req.getEnabledForPos() != null) policy.setEnabledForPos(req.getEnabledForPos());
        if (req.getEnabledForPedido() != null) policy.setEnabledForPedido(req.getEnabledForPedido());
        if (req.getEnabledForFundoConsumo() != null) policy.setEnabledForFundoConsumo(req.getEnabledForFundoConsumo());
        if (req.getMinAmount() != null) policy.setMinAmount(req.getMinAmount());
        if (req.getMaxAmount() != null) policy.setMaxAmount(req.getMaxAmount());
        if (req.getOverrideReason() != null) policy.setOverrideReason(req.getOverrideReason());
        if (req.getMetadata() != null) policy.setMetadataJson(writeMetadata(req.getMetadata()));
        policy.setUpdatedBy(ctx.userId());

        validateMinMax(policy.getMinAmount(), policy.getMaxAmount());
        validatePolicyCompatibility(tenantMethod, policy);

        UnidadePaymentMethodPolicy saved = policyRepository.save(policy);

        Map<String, Object> details = new java.util.HashMap<>();
        details.put("unidadeId", unidadeId);
        details.put("code", code.name());
        details.put("statusAnterior", anterior != null ? anterior.name() : null);
        details.put("statusNovo", saved.getStatus() != null ? saved.getStatus().name() : null);
        details.put("inheritFromTenant", saved.isInheritFromTenant());
        details.put("enabledForQr", saved.getEnabledForQr());
        details.put("enabledForPos", saved.getEnabledForPos());
        details.put("enabledForPedido", saved.getEnabledForPedido());
        details.put("enabledForFundoConsumo", saved.getEnabledForFundoConsumo());
        details.put("minAmount", saved.getMinAmount());
        details.put("maxAmount", saved.getMaxAmount());

        operationalEventLogService.logPublicEvent(
                tenantMethod.getTenant(), null, unidade, null, null,
                OperationalEventType.PAYMENT_METHOD_UNIT_POLICY_UPDATED,
                OperationalEntityType.UNIDADE_PAYMENT_METHOD_POLICY,
                saved.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Política de método de pagamento (unidade) atualizada",
                details,
                ip, userAgent
        );

        return saved;
    }

    @Transactional
    public void remove(Long unidadeId, PaymentMethodCode code, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = TenantContextHolder.require();
        ensureUnidade(ctx.tenantId(), unidadeId);
        UnidadePaymentMethodPolicy policy = policyRepository.findByTenant_IdAndUnidadeAtendimento_IdAndPaymentMethodCode(ctx.tenantId(), unidadeId, code)
                .orElseThrow(() -> new ResourceNotFoundException("Política não encontrada."));
        policyRepository.delete(policy);
        TenantPaymentMethod tm = tenantPaymentMethodService.getOrThrow(ctx.tenantId(), code);
        operationalEventLogService.logPublicEvent(
                tm.getTenant(), null, policy.getUnidadeAtendimento(), null, null,
                OperationalEventType.PAYMENT_METHOD_UNIT_POLICY_REMOVED,
                OperationalEntityType.UNIDADE_PAYMENT_METHOD_POLICY,
                policy.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Política de método de pagamento (unidade) removida",
                Map.of("unidadeId", unidadeId, "code", code.name()),
                ip, userAgent
        );
    }

    private UnidadeAtendimento ensureUnidade(Long tenantId, Long unidadeId) {
        return unidadeAtendimentoRepository.findByIdAndTenantId(unidadeId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Unidade não encontrada."));
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

    private void validatePolicyCompatibility(TenantPaymentMethod tenantMethod, UnidadePaymentMethodPolicy policy) {
        if (!policy.isInheritFromTenant()) {
            // níveis inferiores não podem "expandir" acima do tenant: se tenant já desabilitou um canal, policy não pode tornar true.
            if (policy.getEnabledForQr() != null && policy.getEnabledForQr() && !tenantMethod.isEnabledForQr()) {
                throw new BusinessException("Política inválida: tenant bloqueia QR.");
            }
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
