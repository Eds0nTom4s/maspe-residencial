package com.restaurante.financeiro.paymentmethod.service;

import com.restaurante.dto.response.AvailablePaymentMethodResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.financeiro.paymentmethod.entity.DevicePaymentMethodPolicy;
import com.restaurante.financeiro.paymentmethod.entity.TenantPaymentMethod;
import com.restaurante.financeiro.paymentmethod.entity.UnidadePaymentMethodPolicy;
import com.restaurante.financeiro.paymentmethod.repository.DevicePaymentMethodPolicyRepository;
import com.restaurante.financeiro.paymentmethod.repository.TenantPaymentMethodRepository;
import com.restaurante.financeiro.paymentmethod.repository.UnidadePaymentMethodPolicyRepository;
import com.restaurante.model.enums.*;
import com.restaurante.security.device.DevicePrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentMethodPolicyResolutionService {

    private final TenantPaymentMethodService tenantPaymentMethodService;
    private final TenantPaymentMethodRepository tenantPaymentMethodRepository;
    private final UnidadePaymentMethodPolicyRepository unidadePolicyRepository;
    private final DevicePaymentMethodPolicyRepository devicePolicyRepository;

    @Transactional(readOnly = true)
    public List<AvailablePaymentMethodResponse> listEffectiveForQr(Long tenantId, Long unidadeAtendimentoId, PaymentDestination destination) {
        tenantPaymentMethodService.ensureDefaultsForTenant(tenantId);
        List<TenantPaymentMethod> base = tenantPaymentMethodService.listAvailableForContext(tenantId, PaymentUsageContext.QR_PUBLICO, destination);

        Map<PaymentMethodCode, UnidadePaymentMethodPolicy> unidadePolicies = loadUnidadePolicies(tenantId, unidadeAtendimentoId);

        return base.stream()
                .map(m -> resolveForListing(m, unidadePolicies.get(m.getCode()), null))
                .filter(EffectivePolicy::allowed)
                .sorted(Comparator.comparingInt(p -> p.method.getSortOrder()))
                .map(p -> toAvailableResponse(p.method, p.effectiveMin, p.effectiveMax))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AvailablePaymentMethodResponse> listEffectiveForDevice(DevicePrincipal device, PaymentDestination destination) {
        Long tenantId = device.tenantId();
        Long unidadeId = device.unidadeAtendimentoId();
        tenantPaymentMethodService.ensureDefaultsForTenant(tenantId);
        List<TenantPaymentMethod> base = tenantPaymentMethodService.listAvailableForContext(tenantId, PaymentUsageContext.DEVICE_POS, destination);

        Map<PaymentMethodCode, UnidadePaymentMethodPolicy> unidadePolicies = loadUnidadePolicies(tenantId, unidadeId);
        Map<PaymentMethodCode, DevicePaymentMethodPolicy> devicePolicies = loadDevicePolicies(tenantId, device.dispositivoId());

        return base.stream()
                .map(m -> resolveForListing(m, unidadePolicies.get(m.getCode()), devicePolicies.get(m.getCode())))
                .filter(p -> {
                    if (!p.allowed()) return false;
                    // tornar lista "acionável": se o método exige manual/gateway, device precisa estar apto.
                    if (p.method.isRequiresManualConfirmation()) {
                        return Boolean.TRUE.equals(p.canConfirmManual);
                    }
                    if (p.method.isRequiresGateway()) {
                        return Boolean.TRUE.equals(p.canStartGateway);
                    }
                    return true;
                })
                .sorted(Comparator.comparingInt(p -> p.method.getSortOrder()))
                .map(p -> toAvailableResponse(p.method, p.effectiveMin, p.effectiveMax))
                .toList();
    }

    @Transactional(readOnly = true)
    public void validateForQr(Long tenantId,
                              Long unidadeAtendimentoId,
                              PaymentMethodCode code,
                              PaymentDestination destination,
                              BigDecimal amount) {
        tenantPaymentMethodService.ensureDefaultsForTenant(tenantId);
        TenantPaymentMethod method = tenantPaymentMethodService.validateMethodAllowed(tenantId, code, PaymentUsageContext.QR_PUBLICO, destination, amount);

        Map<PaymentMethodCode, UnidadePaymentMethodPolicy> unidadePolicies = loadUnidadePolicies(tenantId, unidadeAtendimentoId);
        EffectivePolicy eff = resolveWithAmount(method, unidadePolicies.get(code), null, amount);
        if (!eff.allowed()) throw new BusinessException("Método bloqueado pela política (QR/unidade).");
    }

    @Transactional(readOnly = true)
    public void validateGatewayStartQr(Long tenantId,
                                       Long unidadeAtendimentoId,
                                       PaymentMethodCode code,
                                       PaymentDestination destination,
                                       BigDecimal amount) {
        validateForQr(tenantId, unidadeAtendimentoId, code, destination, amount);
        TenantPaymentMethod method = tenantPaymentMethodRepository.findByTenantIdAndCode(tenantId, code).orElseThrow();
        if (!method.isRequiresGateway()) {
            throw new BusinessException("Método não é gateway.");
        }
    }

    @Transactional(readOnly = true)
    public void validateForDevice(DevicePrincipal device,
                                  PaymentMethodCode code,
                                  PaymentDestination destination,
                                  BigDecimal amount) {
        Long tenantId = device.tenantId();
        Long unidadeId = device.unidadeAtendimentoId();
        tenantPaymentMethodService.ensureDefaultsForTenant(tenantId);
        TenantPaymentMethod method = tenantPaymentMethodService.validateMethodAllowed(tenantId, code, PaymentUsageContext.DEVICE_POS, destination, amount);

        Map<PaymentMethodCode, UnidadePaymentMethodPolicy> unidadePolicies = loadUnidadePolicies(tenantId, unidadeId);
        Map<PaymentMethodCode, DevicePaymentMethodPolicy> devicePolicies = loadDevicePolicies(tenantId, device.dispositivoId());
        EffectivePolicy eff = resolveWithAmount(method, unidadePolicies.get(code), devicePolicies.get(code), amount);
        if (!eff.allowed()) throw new BusinessException("Método bloqueado pela política (device/unidade).");
    }

    @Transactional(readOnly = true)
    public void validateGatewayStartDevice(DevicePrincipal device,
                                           PaymentMethodCode code,
                                           PaymentDestination destination,
                                           BigDecimal amount) {
        validateForDevice(device, code, destination, amount);
        TenantPaymentMethod method = tenantPaymentMethodRepository.findByTenantIdAndCode(device.tenantId(), code).orElseThrow();
        if (!method.isRequiresGateway()) {
            throw new BusinessException("Método não é gateway.");
        }
        Boolean canStartGateway = resolveCanStartGateway(device, code);
        if (!Boolean.TRUE.equals(canStartGateway)) {
            throw new BusinessException("Device não está autorizado a iniciar gateway para este método.");
        }
    }

    @Transactional(readOnly = true)
    public void validateManualConfirmation(DevicePrincipal device,
                                           PaymentMethodCode code,
                                           PaymentDestination destination,
                                           BigDecimal amount) {
        validateForDevice(device, code, destination, amount);
        TenantPaymentMethod method = tenantPaymentMethodRepository.findByTenantIdAndCode(device.tenantId(), code).orElseThrow();
        if (!method.isRequiresManualConfirmation()) {
            throw new BusinessException("Método não é manual.");
        }
        Boolean canConfirmManual = resolveCanConfirmManual(device, code);
        if (!Boolean.TRUE.equals(canConfirmManual)) {
            throw new BusinessException("Device não está autorizado a confirmar manualmente este método.");
        }
    }

    private Boolean resolveCanConfirmManual(DevicePrincipal device, PaymentMethodCode code) {
        DevicePaymentMethodPolicy dp = devicePolicyRepository.findByTenant_IdAndDispositivoOperacional_IdAndPaymentMethodCode(device.tenantId(), device.dispositivoId(), code)
                .orElse(null);
        if (dp != null && !dp.isInheritFromUnidade() && dp.getCanConfirmManual() != null) return dp.getCanConfirmManual();
        UnidadePaymentMethodPolicy up = unidadePolicyRepository.findByTenant_IdAndUnidadeAtendimento_IdAndPaymentMethodCode(device.tenantId(), device.unidadeAtendimentoId(), code)
                .orElse(null);
        if (up != null && !up.isInheritFromTenant()) {
            return Boolean.TRUE; // unidade não controla canConfirmManual; herda implicitamente
        }
        return Boolean.TRUE;
    }

    private Boolean resolveCanStartGateway(DevicePrincipal device, PaymentMethodCode code) {
        DevicePaymentMethodPolicy dp = devicePolicyRepository.findByTenant_IdAndDispositivoOperacional_IdAndPaymentMethodCode(device.tenantId(), device.dispositivoId(), code)
                .orElse(null);
        if (dp != null && !dp.isInheritFromUnidade() && dp.getCanStartGateway() != null) return dp.getCanStartGateway();
        return Boolean.TRUE;
    }

    private Map<PaymentMethodCode, UnidadePaymentMethodPolicy> loadUnidadePolicies(Long tenantId, Long unidadeId) {
        if (unidadeId == null) return Map.of();
        List<UnidadePaymentMethodPolicy> list = unidadePolicyRepository.findByTenant_IdAndUnidadeAtendimento_Id(tenantId, unidadeId);
        Map<PaymentMethodCode, UnidadePaymentMethodPolicy> map = new EnumMap<>(PaymentMethodCode.class);
        for (UnidadePaymentMethodPolicy p : list) map.put(p.getPaymentMethodCode(), p);
        return map;
    }

    private Map<PaymentMethodCode, DevicePaymentMethodPolicy> loadDevicePolicies(Long tenantId, Long deviceId) {
        if (deviceId == null) return Map.of();
        List<DevicePaymentMethodPolicy> list = devicePolicyRepository.findByTenant_IdAndDispositivoOperacional_Id(tenantId, deviceId);
        Map<PaymentMethodCode, DevicePaymentMethodPolicy> map = new EnumMap<>(PaymentMethodCode.class);
        for (DevicePaymentMethodPolicy p : list) map.put(p.getPaymentMethodCode(), p);
        return map;
    }

    private EffectivePolicy resolveEffective(TenantPaymentMethod method,
                                             UnidadePaymentMethodPolicy unidadePolicy,
                                             DevicePaymentMethodPolicy devicePolicy,
                                             PaymentUsageContext context,
                                             PaymentDestination destination,
                                             BigDecimal amount) {
        throw new UnsupportedOperationException("Use resolveForListing/resolveWithAmount");
    }

    private EffectivePolicy resolveForListing(TenantPaymentMethod method,
                                              UnidadePaymentMethodPolicy unidadePolicy,
                                              DevicePaymentMethodPolicy devicePolicy) {
        ResolvedFlags unitFlags = applyUnidade(method, unidadePolicy);
        if (!unitFlags.allowed) return EffectivePolicy.blocked(method, unitFlags.blockedReason);
        ResolvedFlags deviceFlags = applyDevice(method, unitFlags, devicePolicy);
        if (!deviceFlags.allowed) return EffectivePolicy.blocked(method, deviceFlags.blockedReason);

        BigDecimal effMin = maxOf(method.getMinAmount(), unitFlags.minAmount, deviceFlags.minAmount);
        BigDecimal effMax = minOf(method.getMaxAmount(), unitFlags.maxAmount, deviceFlags.maxAmount);
        if (effMin != null && effMax != null && effMax.compareTo(effMin) < 0) {
            return EffectivePolicy.blocked(method, "PAYMENT_METHOD_AMOUNT_POLICY_CONFLICT");
        }
        EffectivePolicy ok = EffectivePolicy.allowed(method);
        ok.effectiveMin = effMin;
        ok.effectiveMax = effMax;
        ok.canConfirmManual = deviceFlags.canConfirmManual;
        ok.canStartGateway = deviceFlags.canStartGateway;
        return ok;
    }

    private EffectivePolicy resolveWithAmount(TenantPaymentMethod method,
                                              UnidadePaymentMethodPolicy unidadePolicy,
                                              DevicePaymentMethodPolicy devicePolicy,
                                              BigDecimal amount) {
        EffectivePolicy base = resolveForListing(method, unidadePolicy, devicePolicy);
        if (!base.allowed()) return base;
        if (amount != null) {
            BigDecimal effMin = base.effectiveMin;
            BigDecimal effMax = base.effectiveMax;
            if (effMin != null && amount.compareTo(effMin) < 0) return EffectivePolicy.blocked(method, "PAYMENT_METHOD_AMOUNT_BELOW_MIN");
            if (effMax != null && amount.compareTo(effMax) > 0) return EffectivePolicy.blocked(method, "PAYMENT_METHOD_AMOUNT_ABOVE_MAX");
        }
        return base;
    }

    private ResolvedFlags applyUnidade(TenantPaymentMethod tenantMethod, UnidadePaymentMethodPolicy policy) {
        ResolvedFlags flags = ResolvedFlags.allowed();
        if (policy == null) return flags;
        if (!policy.isInheritFromTenant() && policy.getStatus() != null && policy.getStatus() != PaymentMethodPolicyStatus.INHERIT) {
            if (policy.getStatus() == PaymentMethodPolicyStatus.BLOCK) return ResolvedFlags.blocked("PAYMENT_METHOD_UNIT_POLICY_BLOCKED");
            if (policy.getStatus() == PaymentMethodPolicyStatus.SUSPENDED) return ResolvedFlags.blocked("PAYMENT_METHOD_UNIT_POLICY_SUSPENDED");
        }
        // overrides (apenas restringem)
        flags.enabledForQr = andMaybe(tenantMethod.isEnabledForQr(), policy.getEnabledForQr());
        flags.enabledForPos = andMaybe(tenantMethod.isEnabledForPos(), policy.getEnabledForPos());
        flags.enabledForPedido = andMaybe(tenantMethod.isEnabledForPedido(), policy.getEnabledForPedido());
        flags.enabledForFundoConsumo = andMaybe(tenantMethod.isEnabledForFundoConsumo(), policy.getEnabledForFundoConsumo());
        flags.minAmount = policy.getMinAmount();
        flags.maxAmount = policy.getMaxAmount();
        return flags;
    }

    private ResolvedFlags applyDevice(TenantPaymentMethod tenantMethod, ResolvedFlags unidadeFlags, DevicePaymentMethodPolicy policy) {
        ResolvedFlags flags = unidadeFlags.copy();
        flags.canConfirmManual = Boolean.TRUE;
        flags.canStartGateway = Boolean.TRUE;

        if (policy == null) return flags;
        if (!policy.isInheritFromUnidade() && policy.getStatus() != null && policy.getStatus() != PaymentMethodPolicyStatus.INHERIT) {
            if (policy.getStatus() == PaymentMethodPolicyStatus.BLOCK) return ResolvedFlags.blocked("PAYMENT_METHOD_DEVICE_POLICY_BLOCKED");
            if (policy.getStatus() == PaymentMethodPolicyStatus.SUSPENDED) return ResolvedFlags.blocked("PAYMENT_METHOD_DEVICE_POLICY_SUSPENDED");
        }

        flags.enabledForPos = andMaybe(flags.enabledForPos, policy.getEnabledForPos());
        flags.enabledForPedido = andMaybe(flags.enabledForPedido, policy.getEnabledForPedido());
        flags.enabledForFundoConsumo = andMaybe(flags.enabledForFundoConsumo, policy.getEnabledForFundoConsumo());
        if (policy.getMinAmount() != null) flags.minAmount = policy.getMinAmount();
        if (policy.getMaxAmount() != null) flags.maxAmount = policy.getMaxAmount();
        if (policy.getCanConfirmManual() != null) flags.canConfirmManual = policy.getCanConfirmManual();
        if (policy.getCanStartGateway() != null) flags.canStartGateway = policy.getCanStartGateway();
        return flags;
    }

    private AvailablePaymentMethodResponse toAvailableResponse(TenantPaymentMethod m, BigDecimal effMin, BigDecimal effMax) {
        AvailablePaymentMethodResponse r = new AvailablePaymentMethodResponse();
        r.setCode(m.getCode());
        r.setDisplayName(m.getDisplayName());
        r.setDescription(m.getDescription());
        r.setType(m.getType());
        r.setConfirmationMode(m.getConfirmationMode());
        r.setProvider(m.getProvider());
        r.setRequiresOpenTurno(m.isRequiresOpenTurno());
        r.setMinAmount(effMin != null ? effMin : m.getMinAmount());
        r.setMaxAmount(effMax != null ? effMax : m.getMaxAmount());
        r.setCurrency(m.getCurrency());
        r.setSortOrder(m.getSortOrder());
        r.setIconKey(m.getIconKey());
        return r;
    }

    private boolean andMaybe(boolean base, Boolean override) {
        if (override == null) return base;
        return base && override;
    }

    private BigDecimal maxOf(BigDecimal... values) {
        BigDecimal max = null;
        for (BigDecimal v : values) {
            if (v == null) continue;
            if (max == null || v.compareTo(max) > 0) max = v;
        }
        return max;
    }

    private BigDecimal minOf(BigDecimal... values) {
        BigDecimal min = null;
        for (BigDecimal v : values) {
            if (v == null) continue;
            if (min == null || v.compareTo(min) < 0) min = v;
        }
        return min;
    }

    private static final class ResolvedFlags {
        boolean allowed;
        String blockedReason;
        boolean enabledForQr = true;
        boolean enabledForPos = true;
        boolean enabledForPedido = true;
        boolean enabledForFundoConsumo = true;
        BigDecimal minAmount;
        BigDecimal maxAmount;
        Boolean canConfirmManual = Boolean.TRUE;
        Boolean canStartGateway = Boolean.TRUE;

        static ResolvedFlags allowed() { return new ResolvedFlags(true, null); }
        static ResolvedFlags blocked(String reason) { return new ResolvedFlags(false, reason); }

        ResolvedFlags(boolean allowed, String reason) {
            this.allowed = allowed;
            this.blockedReason = reason;
        }

        ResolvedFlags copy() {
            ResolvedFlags c = new ResolvedFlags(this.allowed, this.blockedReason);
            c.enabledForQr = this.enabledForQr;
            c.enabledForPos = this.enabledForPos;
            c.enabledForPedido = this.enabledForPedido;
            c.enabledForFundoConsumo = this.enabledForFundoConsumo;
            c.minAmount = this.minAmount;
            c.maxAmount = this.maxAmount;
            c.canConfirmManual = this.canConfirmManual;
            c.canStartGateway = this.canStartGateway;
            return c;
        }
    }

    private static final class EffectivePolicy {
        final TenantPaymentMethod method;
        boolean allowed;
        String blockedReason;
        BigDecimal effectiveMin;
        BigDecimal effectiveMax;
        Boolean canConfirmManual;
        Boolean canStartGateway;

        static EffectivePolicy allowed(TenantPaymentMethod method) {
            EffectivePolicy p = new EffectivePolicy(method);
            p.allowed = true;
            return p;
        }

        static EffectivePolicy blocked(TenantPaymentMethod method, String reason) {
            EffectivePolicy p = new EffectivePolicy(method);
            p.allowed = false;
            p.blockedReason = reason;
            return p;
        }

        private EffectivePolicy(TenantPaymentMethod method) {
            this.method = method;
        }

        boolean allowed() { return allowed; }
    }
}
