package com.restaurante.financeiro.paymentmethod.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.UpdateTenantPaymentMethodRequest;
import com.restaurante.exception.BusinessException;
import com.restaurante.financeiro.gateway.appypay.AppyPayProperties;
import com.restaurante.financeiro.paymentmethod.entity.TenantPaymentMethod;
import com.restaurante.financeiro.paymentmethod.repository.TenantPaymentMethodRepository;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodStatus;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantPaymentMethodAdminService {

    private final TenantGuard tenantGuard;
    private final TenantPaymentMethodRepository repository;
    private final TenantPaymentMethodService methodService;
    private final OperationalEventLogService operationalEventLogService;
    private final ObjectMapper objectMapper;
    private final AppyPayProperties appyPayProperties;

    @Value("${consuma.financeiro.payment-methods.allow-no-active-method:false}")
    private boolean allowNoActiveMethod;

    @Transactional
    public List<TenantPaymentMethod> listar() {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_FINANCE,
                TenantUserRole.TENANT_CASHIER
        );
        TenantContext ctx = tenantGuard.requireContext();
        return methodService.listForTenant(ctx.tenantId());
    }

    @Transactional
    public TenantPaymentMethod buscar(PaymentMethodCode code) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_FINANCE,
                TenantUserRole.TENANT_CASHIER
        );
        TenantContext ctx = tenantGuard.requireContext();
        return methodService.getOrThrow(ctx.tenantId(), code);
    }

    @Transactional
    public TenantPaymentMethod atualizar(PaymentMethodCode code, UpdateTenantPaymentMethodRequest req, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_FINANCE
        );
        TenantContext ctx = TenantContextHolder.require();
        TenantPaymentMethod m = methodService.getOrThrow(ctx.tenantId(), code);

        PaymentMethodStatus statusAnterior = m.getStatus();

        if (req.getDisplayName() != null && !req.getDisplayName().isBlank()) m.setDisplayName(req.getDisplayName().trim());
        if (req.getDescription() != null) m.setDescription(req.getDescription().trim());

        if (req.getEnabledForQr() != null) m.setEnabledForQr(req.getEnabledForQr());
        if (req.getEnabledForPos() != null) m.setEnabledForPos(req.getEnabledForPos());
        if (req.getEnabledForPedido() != null) m.setEnabledForPedido(req.getEnabledForPedido());
        if (req.getEnabledForFundoConsumo() != null) m.setEnabledForFundoConsumo(req.getEnabledForFundoConsumo());

        if (req.getMinAmount() != null) m.setMinAmount(req.getMinAmount());
        if (req.getMaxAmount() != null) m.setMaxAmount(req.getMaxAmount());
        if (m.getMinAmount() != null && m.getMinAmount().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new BusinessException("Configuração inválida: minAmount negativo.");
        }
        if (m.getMaxAmount() != null && m.getMaxAmount().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new BusinessException("Configuração inválida: maxAmount negativo.");
        }
        if (m.getMinAmount() != null && m.getMaxAmount() != null && m.getMaxAmount().compareTo(m.getMinAmount()) < 0) {
            throw new BusinessException("Configuração inválida: maxAmount < minAmount.");
        }

        if (req.getSortOrder() != null) m.setSortOrder(req.getSortOrder());
        if (req.getIconKey() != null) m.setIconKey(req.getIconKey().trim());
        if (req.getMetadata() != null) m.setMetadataJson(writeJson(req.getMetadata()));

        if (req.getStatus() != null && req.getStatus() != m.getStatus()) {
            if (req.getStatus() == PaymentMethodStatus.ACTIVE && code == PaymentMethodCode.APPYPAY) {
                if (!isAppyPayConfigured()) {
                    throw new BusinessException("Não é possível ativar APPYPAY: configuração do gateway ausente.");
                }
            }
            // Aplicar regra de não deixar tenant sem método ativo para PEDIDO (default)
            if (!allowNoActiveMethod && req.getStatus() != PaymentMethodStatus.ACTIVE) {
                if (m.isEnabledForPedido() && m.getStatus() == PaymentMethodStatus.ACTIVE) {
                    long outrosAtivos = repository.findByTenantIdAndStatusOrderBySortOrderAscCodeAsc(ctx.tenantId(), PaymentMethodStatus.ACTIVE)
                            .stream()
                            .filter(x -> x.getCode() != m.getCode())
                            .filter(TenantPaymentMethod::isEnabledForPedido)
                            .count();
                    if (outrosAtivos == 0) {
                        throw new BusinessException("Não é permitido desativar o último método ativo para PEDIDO.");
                    }
                }
            }
            m.setStatus(req.getStatus());
        }

        TenantPaymentMethod saved = repository.save(m);

        Tenant tenant = saved.getTenant();
        Map<String, Object> meta = new HashMap<>();
        meta.put("code", saved.getCode() != null ? saved.getCode().name() : null);
        if (statusAnterior != null) meta.put("statusAnterior", statusAnterior.name());
        if (saved.getStatus() != null) meta.put("statusNovo", saved.getStatus().name());
        meta.put("enabledForQr", saved.isEnabledForQr());
        meta.put("enabledForPos", saved.isEnabledForPos());
        meta.put("enabledForPedido", saved.isEnabledForPedido());
        meta.put("enabledForFundoConsumo", saved.isEnabledForFundoConsumo());
        meta.put("minAmount", saved.getMinAmount());
        meta.put("maxAmount", saved.getMaxAmount());
        operationalEventLogService.logPublicEvent(
                tenant,
                null,
                null,
                null,
                null,
                OperationalEventType.PAYMENT_METHOD_UPDATED,
                OperationalEntityType.TENANT_PAYMENT_METHOD,
                saved.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Método de pagamento atualizado",
                meta,
                ip,
                userAgent
        );

        return saved;
    }

    @Transactional
    public TenantPaymentMethod activate(PaymentMethodCode code, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = TenantContextHolder.require();
        TenantPaymentMethod m = methodService.getOrThrow(ctx.tenantId(), code);
        if (m.getStatus() == PaymentMethodStatus.ACTIVE) return m;
        PaymentMethodStatus anterior = m.getStatus();
        if (code == PaymentMethodCode.APPYPAY && !isAppyPayConfigured()) {
            throw new BusinessException("Não é possível ativar APPYPAY: configuração do gateway ausente.");
        }
        m.setStatus(PaymentMethodStatus.ACTIVE);
        TenantPaymentMethod saved = repository.save(m);
        Map<String, Object> meta = new HashMap<>();
        meta.put("code", saved.getCode() != null ? saved.getCode().name() : null);
        if (anterior != null) meta.put("statusAnterior", anterior.name());
        operationalEventLogService.logPublicEvent(
                saved.getTenant(), null, null, null, null,
                OperationalEventType.PAYMENT_METHOD_ACTIVATED,
                OperationalEntityType.TENANT_PAYMENT_METHOD,
                saved.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Método de pagamento ativado",
                meta,
                ip, userAgent
        );
        return saved;
    }

    @Transactional
    public TenantPaymentMethod deactivate(PaymentMethodCode code, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN, TenantUserRole.TENANT_FINANCE);
        TenantContext ctx = TenantContextHolder.require();
        TenantPaymentMethod m = methodService.getOrThrow(ctx.tenantId(), code);
        if (m.getStatus() == PaymentMethodStatus.INACTIVE) return m;

        if (!allowNoActiveMethod && m.getStatus() == PaymentMethodStatus.ACTIVE && m.isEnabledForPedido()) {
            long outrosAtivos = repository.findByTenantIdAndStatusOrderBySortOrderAscCodeAsc(ctx.tenantId(), PaymentMethodStatus.ACTIVE)
                    .stream()
                    .filter(x -> x.getCode() != m.getCode())
                    .filter(TenantPaymentMethod::isEnabledForPedido)
                    .count();
            if (outrosAtivos == 0) {
                throw new BusinessException("Não é permitido desativar o último método ativo para PEDIDO.");
            }
        }

        PaymentMethodStatus anterior = m.getStatus();
        m.setStatus(PaymentMethodStatus.INACTIVE);
        TenantPaymentMethod saved = repository.save(m);
        Map<String, Object> meta = new HashMap<>();
        meta.put("code", saved.getCode() != null ? saved.getCode().name() : null);
        if (anterior != null) meta.put("statusAnterior", anterior.name());
        operationalEventLogService.logPublicEvent(
                saved.getTenant(), null, null, null, null,
                OperationalEventType.PAYMENT_METHOD_DEACTIVATED,
                OperationalEntityType.TENANT_PAYMENT_METHOD,
                saved.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Método de pagamento desativado",
                meta,
                ip, userAgent
        );
        return saved;
    }

    private boolean isAppyPayConfigured() {
        if (appyPayProperties == null) return false;
        if (appyPayProperties.isMock()) return true;
        return appyPayProperties.getBaseUrl() != null && !appyPayProperties.getBaseUrl().isBlank()
                && appyPayProperties.getTokenUrl() != null && !appyPayProperties.getTokenUrl().isBlank()
                && appyPayProperties.getClientId() != null && !appyPayProperties.getClientId().isBlank()
                && appyPayProperties.getClientSecret() != null && !appyPayProperties.getClientSecret().isBlank()
                && appyPayProperties.getResource() != null && !appyPayProperties.getResource().isBlank();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> readMetadata(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BusinessException("Falha ao serializar metadata.");
        }
    }
}
