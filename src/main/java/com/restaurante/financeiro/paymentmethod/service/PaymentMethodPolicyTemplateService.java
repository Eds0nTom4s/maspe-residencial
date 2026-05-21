package com.restaurante.financeiro.paymentmethod.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.CreatePaymentPolicyTemplateRequest;
import com.restaurante.dto.request.PaymentPolicyTemplateItemRequest;
import com.restaurante.dto.request.UpdatePaymentPolicyTemplateRequest;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.financeiro.paymentmethod.entity.PaymentMethodPolicyTemplate;
import com.restaurante.financeiro.paymentmethod.entity.PaymentMethodPolicyTemplateItem;
import com.restaurante.financeiro.paymentmethod.repository.PaymentMethodPolicyTemplateRepository;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.*;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PaymentMethodPolicyTemplateService {

    private final TenantGuard tenantGuard;
    private final PaymentMethodPolicyTemplateRepository templateRepository;
    private final TenantPaymentMethodService tenantPaymentMethodService;
    private final PaymentMethodPolicyTemplateBootstrapService bootstrapService;
    private final OperationalEventLogService operationalEventLogService;
    private final ObjectMapper objectMapper;
    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public List<PaymentMethodPolicyTemplate> listTemplates() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE, TenantUserRole.TENANT_CASHIER);
        TenantContext ctx = tenantGuard.requireContext();
        bootstrapService.ensureDefaults(ctx.tenantId());
        return templateRepository.findByTenant_IdOrderByIdDesc(ctx.tenantId());
    }

    @Transactional(readOnly = true)
    public PaymentMethodPolicyTemplate getTemplateWithItems(Long templateId) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE, TenantUserRole.TENANT_CASHIER);
        TenantContext ctx = tenantGuard.requireContext();
        bootstrapService.ensureDefaults(ctx.tenantId());
        return templateRepository.findWithItemsByIdAndTenant_Id(templateId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Template não encontrado."));
    }

    @Transactional
    public PaymentMethodPolicyTemplate create(CreatePaymentPolicyTemplateRequest req, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = TenantContextHolder.require();
        Tenant tenant = tenantRepository.findById(ctx.tenantId()).orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        tenantPaymentMethodService.ensureDefaultsForTenant(ctx.tenantId());

        String code = normalizeCode(req.getCode());
        if (templateRepository.findByTenant_IdAndCode(ctx.tenantId(), code).isPresent()) {
            throw new BusinessException("Já existe template com esse code.");
        }

        PaymentMethodPolicyTemplate t = new PaymentMethodPolicyTemplate();
        t.setTenant(tenant);
        t.setCode(code);
        t.setName(req.getName());
        t.setDescription(req.getDescription());
        t.setTargetDeviceType(req.getTargetDeviceType());
        t.setStatus(req.getStatus());
        t.setSystemDefault(false);
        t.setVersion(1);
        t.setCreatedBy(ctx.userId());

        List<PaymentMethodPolicyTemplateItem> items = mapAndValidateItems(ctx.tenantId(), t, req.getItems());
        t.getItems().clear();
        if (items != null) t.getItems().addAll(items);

        PaymentMethodPolicyTemplate saved = templateRepository.save(t);

        operationalEventLogService.logPublicEvent(
                tenant,
                null,
                null,
                null,
                null,
                OperationalEventType.PAYMENT_POLICY_TEMPLATE_CREATED,
                OperationalEntityType.PAYMENT_POLICY_TEMPLATE,
                saved.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Template de policy criado",
                Map.of("templateId", saved.getId(), "templateCode", saved.getCode(), "status", saved.getStatus().name()),
                ip,
                userAgent
        );

        return saved;
    }

    @Transactional
    public PaymentMethodPolicyTemplate update(Long templateId, UpdatePaymentPolicyTemplateRequest req, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = TenantContextHolder.require();
        Tenant tenant = tenantRepository.findById(ctx.tenantId()).orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        tenantPaymentMethodService.ensureDefaultsForTenant(ctx.tenantId());

        PaymentMethodPolicyTemplate t = templateRepository.findWithItemsByIdAndTenant_Id(templateId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Template não encontrado."));

        t.setName(req.getName());
        t.setDescription(req.getDescription());
        t.setTargetDeviceType(req.getTargetDeviceType());
        t.setStatus(req.getStatus());
        t.setVersion(t.getVersion() + 1);
        t.setUpdatedBy(ctx.userId());

        List<PaymentMethodPolicyTemplateItem> items = mapAndValidateItems(ctx.tenantId(), t, req.getItems());
        t.getItems().clear();
        if (items != null) t.getItems().addAll(items);

        PaymentMethodPolicyTemplate saved = templateRepository.save(t);

        operationalEventLogService.logPublicEvent(
                tenant,
                null,
                null,
                null,
                null,
                OperationalEventType.PAYMENT_POLICY_TEMPLATE_UPDATED,
                OperationalEntityType.PAYMENT_POLICY_TEMPLATE,
                saved.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Template de policy atualizado",
                Map.of("templateId", saved.getId(), "templateCode", saved.getCode(), "status", saved.getStatus().name(), "version", saved.getVersion()),
                ip,
                userAgent
        );

        return saved;
    }

    @Transactional
    public PaymentMethodPolicyTemplate activate(Long templateId, String ip, String userAgent) {
        return setStatus(templateId, PaymentMethodPolicyTemplateStatus.ACTIVE, OperationalEventType.PAYMENT_POLICY_TEMPLATE_ACTIVATED, ip, userAgent);
    }

    @Transactional
    public PaymentMethodPolicyTemplate deactivate(Long templateId, String ip, String userAgent) {
        return setStatus(templateId, PaymentMethodPolicyTemplateStatus.INACTIVE, OperationalEventType.PAYMENT_POLICY_TEMPLATE_DEACTIVATED, ip, userAgent);
    }

    private PaymentMethodPolicyTemplate setStatus(Long templateId, PaymentMethodPolicyTemplateStatus status, OperationalEventType event, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = TenantContextHolder.require();
        Tenant tenant = tenantRepository.findById(ctx.tenantId()).orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        PaymentMethodPolicyTemplate t = templateRepository.findByIdAndTenant_Id(templateId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Template não encontrado."));
        t.setStatus(status);
        t.setVersion(t.getVersion() + 1);
        t.setUpdatedBy(ctx.userId());
        PaymentMethodPolicyTemplate saved = templateRepository.save(t);

        operationalEventLogService.logPublicEvent(
                tenant,
                null,
                null,
                null,
                null,
                event,
                OperationalEntityType.PAYMENT_POLICY_TEMPLATE,
                saved.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Status de template de policy alterado",
                Map.of("templateId", saved.getId(), "templateCode", saved.getCode(), "status", saved.getStatus().name()),
                ip,
                userAgent
        );
        return saved;
    }

    private List<PaymentMethodPolicyTemplateItem> mapAndValidateItems(Long tenantId, PaymentMethodPolicyTemplate template, List<PaymentPolicyTemplateItemRequest> reqItems) {
        if (reqItems == null) return Collections.emptyList();

        Tenant tenant = template.getTenant();
        Map<PaymentMethodCode, PaymentPolicyTemplateItemRequest> byCode = new LinkedHashMap<>();
        for (PaymentPolicyTemplateItemRequest r : reqItems) {
            if (r == null || r.getPaymentMethodCode() == null) continue;
            if (byCode.putIfAbsent(r.getPaymentMethodCode(), r) != null) {
                throw new BusinessException("Itens duplicados no template para o método: " + r.getPaymentMethodCode().name());
            }
        }

        List<PaymentMethodPolicyTemplateItem> items = new ArrayList<>();
        for (PaymentPolicyTemplateItemRequest r : byCode.values()) {
            tenantPaymentMethodService.getOrThrow(tenantId, r.getPaymentMethodCode()); // valida que existe no tenant
            validateItem(r);

            PaymentMethodPolicyTemplateItem i = new PaymentMethodPolicyTemplateItem();
            i.setTenant(tenant);
            i.setTemplate(template);
            i.setPaymentMethodCode(r.getPaymentMethodCode());
            i.setPolicyStatus(r.getPolicyStatus());
            i.setEnabledForPos(r.getEnabledForPos());
            i.setEnabledForPedido(r.getEnabledForPedido());
            i.setEnabledForFundoConsumo(r.getEnabledForFundoConsumo());
            i.setCanConfirmManual(r.getCanConfirmManual());
            i.setCanStartGateway(r.getCanStartGateway());
            i.setMinAmount(r.getMinAmount());
            i.setMaxAmount(r.getMaxAmount());
            i.setOverrideReason(r.getOverrideReason());
            i.setMetadataJson(writeMetadata(r.getMetadata()));
            items.add(i);
        }
        return items;
    }

    private void validateItem(PaymentPolicyTemplateItemRequest r) {
        validateMinMax(r.getMinAmount(), r.getMaxAmount());

        if ((r.getPaymentMethodCode() == PaymentMethodCode.CASH || r.getPaymentMethodCode() == PaymentMethodCode.TPA)
                && Boolean.TRUE.equals(r.getCanStartGateway())) {
            throw new BusinessException("Template inválido: CASH/TPA não podem ter canStartGateway=true.");
        }
        if (r.getPaymentMethodCode() == PaymentMethodCode.APPYPAY && Boolean.TRUE.equals(r.getCanConfirmManual())) {
            throw new BusinessException("Template inválido: APPYPAY não pode ter canConfirmManual=true.");
        }
    }

    private void validateMinMax(BigDecimal min, BigDecimal max) {
        if (min != null && min.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException("minAmount não pode ser negativo.");
        if (max != null && max.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException("maxAmount não pode ser negativo.");
        if (min != null && max != null && max.compareTo(min) < 0) throw new BusinessException("maxAmount deve ser >= minAmount.");
    }

    private String normalizeCode(String code) {
        if (code == null) throw new BusinessException("code é obrigatório.");
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) throw new BusinessException("code é obrigatório.");
        if (normalized.length() > 80) throw new BusinessException("code excede 80 caracteres.");
        return normalized;
    }

    private String writeMetadata(Map<String, Object> metadata) {
        if (metadata == null) return null;
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
}
