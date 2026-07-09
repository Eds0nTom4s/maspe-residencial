package com.restaurante.financeiro.paymentmethod.service;

import com.restaurante.financeiro.paymentmethod.entity.PaymentMethodPolicyTemplate;
import com.restaurante.financeiro.paymentmethod.entity.PaymentMethodPolicyTemplateItem;
import com.restaurante.financeiro.paymentmethod.repository.PaymentMethodPolicyTemplateRepository;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.*;
import com.restaurante.repository.TenantRepository;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentMethodPolicyTemplateBootstrapService {

    public static final String CODE_POS_CAIXA_COMPLETO = "POS_CAIXA_COMPLETO";
    public static final String CODE_POS_ATENDIMENTO_SEM_CASH = "POS_ATENDIMENTO_SEM_CASH";
    public static final String CODE_KDS_SEM_PAGAMENTO = "KDS_SEM_PAGAMENTO";
    public static final String CODE_QUIOSQUE_APPYPAY = "QUIOSQUE_APPYPAY";

    private final TenantRepository tenantRepository;
    private final PaymentMethodPolicyTemplateRepository templateRepository;
    private final TenantPaymentMethodService tenantPaymentMethodService;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureDefaults(Long tenantId) {
        if (tenantId == null) return;
        if (templateRepository.existsByTenant_Id(tenantId)) return;

        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) return;

        tenantPaymentMethodService.ensureDefaultsForTenant(tenantId);

        PaymentMethodPolicyTemplate posCaixa = buildPosCaixaCompleto(tenant);
        PaymentMethodPolicyTemplate posAtendimento = buildPosAtendimentoSemCash(tenant);
        PaymentMethodPolicyTemplate kds = buildKdsSemPagamento(tenant);
        PaymentMethodPolicyTemplate quiosque = buildQuiosqueAppyPay(tenant);

        templateRepository.saveAll(List.of(posCaixa, posAtendimento, kds, quiosque));

        operationalEventLogService.logPublicEvent(
                tenant,
                null,
                null,
                null,
                null,
                OperationalEventType.PAYMENT_POLICY_TEMPLATE_DEFAULTS_BOOTSTRAPPED,
                OperationalEntityType.PAYMENT_POLICY_TEMPLATE,
                posCaixa.getId(),
                OperationalOrigem.SYSTEM,
                "Templates padrão de policy bootstrapados para o tenant",
                Map.of("codes", List.of(
                        CODE_POS_CAIXA_COMPLETO,
                        CODE_POS_ATENDIMENTO_SEM_CASH,
                        CODE_KDS_SEM_PAGAMENTO,
                        CODE_QUIOSQUE_APPYPAY
                )),
                null,
                null
        );
    }

    private PaymentMethodPolicyTemplate buildPosCaixaCompleto(Tenant tenant) {
        PaymentMethodPolicyTemplate t = base(tenant, CODE_POS_CAIXA_COMPLETO, "POS caixa (completo)", OperationalDeviceType.POS_CAIXA);
        t.getItems().addAll(List.of(
                allowManual(t, tenant, PaymentMethodCode.CASH, true, true, true),
                allowManual(t, tenant, PaymentMethodCode.TPA, true, true, true),
                allowGateway(t, tenant, PaymentMethodCode.APPYPAY, true, true, true)
        ));
        return t;
    }

    private PaymentMethodPolicyTemplate buildPosAtendimentoSemCash(Tenant tenant) {
        PaymentMethodPolicyTemplate t = base(tenant, CODE_POS_ATENDIMENTO_SEM_CASH, "POS atendimento (sem CASH)", OperationalDeviceType.POS_ATENDIMENTO);
        t.getItems().addAll(List.of(
                block(t, tenant, PaymentMethodCode.CASH),
                allowManual(t, tenant, PaymentMethodCode.TPA, true, true, true),
                allowGateway(t, tenant, PaymentMethodCode.APPYPAY, true, true, true)
        ));
        return t;
    }

    private PaymentMethodPolicyTemplate buildKdsSemPagamento(Tenant tenant) {
        PaymentMethodPolicyTemplate t = base(tenant, CODE_KDS_SEM_PAGAMENTO, "KDS (sem pagamento)", OperationalDeviceType.KDS_COZINHA);
        t.getItems().addAll(List.of(
                block(t, tenant, PaymentMethodCode.CASH),
                block(t, tenant, PaymentMethodCode.TPA),
                block(t, tenant, PaymentMethodCode.APPYPAY)
        ));
        return t;
    }

    private PaymentMethodPolicyTemplate buildQuiosqueAppyPay(Tenant tenant) {
        PaymentMethodPolicyTemplate t = base(tenant, CODE_QUIOSQUE_APPYPAY, "Quiosque (AppyPay)", OperationalDeviceType.POS_QUIOSQUE);
        t.getItems().addAll(List.of(
                block(t, tenant, PaymentMethodCode.CASH),
                block(t, tenant, PaymentMethodCode.TPA),
                allowGateway(t, tenant, PaymentMethodCode.APPYPAY, true, true, true)
        ));
        return t;
    }

    private PaymentMethodPolicyTemplate base(Tenant tenant, String code, String name, OperationalDeviceType targetType) {
        PaymentMethodPolicyTemplate t = new PaymentMethodPolicyTemplate();
        t.setTenant(tenant);
        t.setCode(code);
        t.setName(name);
        t.setTargetDeviceType(targetType);
        t.setStatus(PaymentMethodPolicyTemplateStatus.ACTIVE);
        t.setSystemDefault(true);
        t.setVersion(1);
        return t;
    }

    private PaymentMethodPolicyTemplateItem block(PaymentMethodPolicyTemplate template, Tenant tenant, PaymentMethodCode code) {
        PaymentMethodPolicyTemplateItem i = new PaymentMethodPolicyTemplateItem();
        i.setTenant(tenant);
        i.setTemplate(template);
        i.setPaymentMethodCode(code);
        i.setPolicyStatus(PaymentMethodPolicyStatus.BLOCK);
        return i;
    }

    private PaymentMethodPolicyTemplateItem allowManual(PaymentMethodPolicyTemplate template, Tenant tenant, PaymentMethodCode code,
                                                       boolean enabledForPos, boolean enabledForPedido, boolean enabledForFundoConsumo) {
        PaymentMethodPolicyTemplateItem i = new PaymentMethodPolicyTemplateItem();
        i.setTenant(tenant);
        i.setTemplate(template);
        i.setPaymentMethodCode(code);
        i.setPolicyStatus(PaymentMethodPolicyStatus.ALLOW);
        i.setEnabledForPos(enabledForPos);
        i.setEnabledForPedido(enabledForPedido);
        i.setEnabledForFundoConsumo(enabledForFundoConsumo);
        i.setCanConfirmManual(true);
        i.setCanStartGateway(false);
        return i;
    }

    private PaymentMethodPolicyTemplateItem allowGateway(PaymentMethodPolicyTemplate template, Tenant tenant, PaymentMethodCode code,
                                                        boolean enabledForPos, boolean enabledForPedido, boolean enabledForFundoConsumo) {
        PaymentMethodPolicyTemplateItem i = new PaymentMethodPolicyTemplateItem();
        i.setTenant(tenant);
        i.setTemplate(template);
        i.setPaymentMethodCode(code);
        i.setPolicyStatus(PaymentMethodPolicyStatus.ALLOW);
        i.setEnabledForPos(enabledForPos);
        i.setEnabledForPedido(enabledForPedido);
        i.setEnabledForFundoConsumo(enabledForFundoConsumo);
        i.setCanConfirmManual(false);
        i.setCanStartGateway(true);
        return i;
    }
}
