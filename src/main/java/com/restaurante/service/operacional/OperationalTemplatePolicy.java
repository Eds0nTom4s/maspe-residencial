package com.restaurante.service.operacional;

import com.restaurante.exception.ConflictException;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.PedidoOrigem;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
public class OperationalTemplatePolicy {

    public static final String TEMPLATE_REST_V1 = "CONSUMA_REST_V1";
    public static final String TEMPLATE_PONTO_V1 = "CONSUMA_PONTO_V1";
    public static final String TEMPLATE_PDV_INTERNO = "PDV_INTERNO";
    public static final String TEMPLATE_KDS = "KDS";
    public static final String TEMPLATE_CAIXA = "CAIXA";
    public static final String TEMPLATE_LEGACY_UNSPECIFIED = "LEGACY_UNSPECIFIED";

    public enum ProductionFlow {
        REQUIRED,
        OPTIONAL,
        NONE
    }

    private static final Set<OperationalOrigem> TENANT_ORDER_OPERATORS = Set.of(
            OperationalOrigem.TENANT_OWNER,
            OperationalOrigem.TENANT_ADMIN,
            OperationalOrigem.TENANT_OPERATOR,
            OperationalOrigem.TENANT_CASHIER
    );

    private static final Set<OperationalOrigem> PAYMENT_CONFIRMERS = Set.of(
            OperationalOrigem.TENANT_OWNER,
            OperationalOrigem.TENANT_ADMIN,
            OperationalOrigem.TENANT_FINANCE,
            OperationalOrigem.TENANT_CASHIER,
            OperationalOrigem.DEVICE_POS,
            OperationalOrigem.GATEWAY
    );

    private static final Set<OperationalOrigem> PRODUCTION_ACTORS = Set.of(
            OperationalOrigem.TENANT_OWNER,
            OperationalOrigem.TENANT_ADMIN,
            OperationalOrigem.TENANT_OPERATOR,
            OperationalOrigem.TENANT_KITCHEN,
            OperationalOrigem.DEVICE_KDS
    );

    public String resolveTemplateCode(Pedido pedido) {
        Tenant tenant = pedido != null ? pedido.getTenant() : null;
        return normalizeTemplateCode(
                tenant != null ? tenant.getTemplateCode() : null,
                tenant != null ? tenant.getTemplateVersion() : null
        );
    }

    public String normalizeTemplateCode(String rawTemplateCode, Integer templateVersion) {
        if (rawTemplateCode == null || rawTemplateCode.isBlank()) {
            return TEMPLATE_LEGACY_UNSPECIFIED;
        }
        String normalized = rawTemplateCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("CONSUMA_REST") || normalized.equals(TEMPLATE_REST_V1)) {
            return TEMPLATE_REST_V1;
        }
        if (normalized.equals("CONSUMA_PONTO") || normalized.equals(TEMPLATE_PONTO_V1)) {
            return TEMPLATE_PONTO_V1;
        }
        if (normalized.equals(TEMPLATE_PDV_INTERNO) || normalized.equals(TEMPLATE_KDS) || normalized.equals(TEMPLATE_CAIXA)) {
            return normalized;
        }
        if (templateVersion != null && normalized.endsWith("_V" + templateVersion)) {
            return normalized;
        }
        return normalized;
    }

    public PedidoOrigem resolvePedidoOrigem(Pedido pedido, OperationalOrigem actor) {
        // 1. Origem explícita persistida tem prioridade máxima.
        if (pedido != null && pedido.getPedidoOrigem() != null) {
            return pedido.getPedidoOrigem();
        }

        // 2. Actor conhecido do dispositivo define a origem quando o campo ainda não foi preenchido.
        if (actor == OperationalOrigem.DEVICE_POS) {
            return PedidoOrigem.DEVICE_POS;
        }
        if (actor == OperationalOrigem.DEVICE_KDS) {
            return PedidoOrigem.DEVICE_KDS;
        }
        if (pedido == null) {
            return PedidoOrigem.UNKNOWN;
        }
        if (actor != null && isTenantOperator(actor)) {
            return PedidoOrigem.OPERADOR_INTERNO;
        }
        if (actor == OperationalOrigem.SYSTEM) {
            return PedidoOrigem.SISTEMA;
        }

        // 3. Fallback conservador para dados legados sem origem explícita.
        if (pedido.getSessaoParticipante() != null) {
            return PedidoOrigem.SESSAO_PARTICIPANTE;
        }
        SessaoConsumo sessao = pedido.getSessaoConsumo();
        if (sessao == null) {
            return PedidoOrigem.LEGADO;
        }
        if (sessao.getMesa() != null) {
            return PedidoOrigem.QR_MESA;
        }
        if (sessao.getQrCodeSessao() != null && !sessao.getQrCodeSessao().isBlank()) {
            return PedidoOrigem.QR_PRINCIPAL;
        }
        return PedidoOrigem.SESSAO_CONSUMO;
    }

    private boolean isTenantOperator(OperationalOrigem actor) {
        return actor == OperationalOrigem.TENANT_OWNER
                || actor == OperationalOrigem.TENANT_ADMIN
                || actor == OperationalOrigem.TENANT_OPERATOR
                || actor == OperationalOrigem.TENANT_CASHIER
                || actor == OperationalOrigem.TENANT_FINANCE
                || actor == OperationalOrigem.TENANT_KITCHEN;
    }

    public boolean requiresAcceptanceBeforePayment(Pedido pedido, OperationalOrigem actor) {
        String template = resolveTemplateCode(pedido);
        PedidoOrigem origem = resolvePedidoOrigem(pedido, actor);
        if (allowsImmediatePayment(template, origem, actor)) {
            return false;
        }
        return requiresAcceptanceBeforePayment(template, origem);
    }

    public boolean requiresAcceptanceBeforePayment(String templateCode, PedidoOrigem origem) {
        PedidoOrigem resolvedOrigem = origem != null ? origem : PedidoOrigem.UNKNOWN;
        if (resolvedOrigem == PedidoOrigem.DEVICE_POS || resolvedOrigem == PedidoOrigem.PDV_INTERNO) {
            return false;
        }
        if (resolvedOrigem == PedidoOrigem.QR_MESA
                || resolvedOrigem == PedidoOrigem.QR_PRINCIPAL
                || resolvedOrigem == PedidoOrigem.QR_PUBLICO
                || resolvedOrigem == PedidoOrigem.SESSAO_PARTICIPANTE) {
            return true;
        }
        return isRestTemplate(templateCode) || !isPontoTemplate(templateCode);
    }

    public boolean allowsImmediatePayment(String templateCode, PedidoOrigem origem, OperationalOrigem actor) {
        if (actor == OperationalOrigem.DEVICE_POS || origem == PedidoOrigem.DEVICE_POS) {
            return true;
        }
        if (origem == PedidoOrigem.PDV_INTERNO) {
            return actor == OperationalOrigem.TENANT_OPERATOR
                    || actor == OperationalOrigem.TENANT_CASHIER
                    || actor == OperationalOrigem.TENANT_ADMIN
                    || actor == OperationalOrigem.TENANT_OWNER;
        }
        return isPontoTemplate(templateCode)
                && origem == PedidoOrigem.OPERADOR_INTERNO
                && actor == OperationalOrigem.TENANT_OPERATOR;
    }

    public boolean requiresTurno(Pedido pedido) {
        return requiresTurno(resolveTemplateCode(pedido), resolvePedidoOrigem(pedido, null));
    }

    public boolean requiresTurno(String templateCode, PedidoOrigem origem) {
        return true;
    }

    public ProductionFlow productionFlow(String templateCode, PedidoOrigem origem) {
        PedidoOrigem resolvedOrigem = origem != null ? origem : PedidoOrigem.UNKNOWN;
        if (resolvedOrigem == PedidoOrigem.DEVICE_KDS || isKdsTemplate(templateCode)) {
            return ProductionFlow.REQUIRED;
        }
        if (isPontoTemplate(templateCode)) {
            return ProductionFlow.OPTIONAL;
        }
        if (resolvedOrigem == PedidoOrigem.QR_MESA
                || resolvedOrigem == PedidoOrigem.QR_PRINCIPAL
                || isRestTemplate(templateCode)) {
            return ProductionFlow.REQUIRED;
        }
        if (resolvedOrigem == PedidoOrigem.DEVICE_POS || resolvedOrigem == PedidoOrigem.PDV_INTERNO) {
            return ProductionFlow.OPTIONAL;
        }
        if (isCaixaTemplate(templateCode) || resolvedOrigem == PedidoOrigem.CAIXA) {
            return ProductionFlow.NONE;
        }
        return ProductionFlow.OPTIONAL;
    }

    public boolean allowsKitchenFlow(String templateCode, PedidoOrigem origem) {
        return productionFlow(templateCode, origem) != ProductionFlow.NONE;
    }

    public boolean canAccept(OperationalOrigem actor, String templateCode, PedidoOrigem origem) {
        return TENANT_ORDER_OPERATORS.contains(actor)
                && origem != PedidoOrigem.DEVICE_POS
                && origem != PedidoOrigem.DEVICE_KDS
                && origem != PedidoOrigem.PDV_INTERNO;
    }

    public boolean canReject(OperationalOrigem actor, String templateCode, PedidoOrigem origem) {
        return canAccept(actor, templateCode, origem);
    }

    public boolean canConfirmPayment(OperationalOrigem actor, String templateCode, PedidoOrigem origem) {
        return PAYMENT_CONFIRMERS.contains(actor)
                && actor != OperationalOrigem.TENANT_KITCHEN
                && actor != OperationalOrigem.DEVICE_KDS;
    }

    public boolean canMoveProduction(OperationalOrigem actor, String templateCode, PedidoOrigem origem) {
        return PRODUCTION_ACTORS.contains(actor) && allowsKitchenFlow(templateCode, origem);
    }

    public void assertCanAcceptPedido(Pedido pedido, OperationalOrigem actor) {
        String template = resolveTemplateCode(pedido);
        PedidoOrigem origem = resolvePedidoOrigem(pedido, actor);
        if (!canAccept(actor, template, origem)) {
            throw new ConflictException("Template/origem do pedido não permite aceite por este ator.");
        }
    }

    public void assertCanRejectPedido(Pedido pedido, OperationalOrigem actor) {
        String template = resolveTemplateCode(pedido);
        PedidoOrigem origem = resolvePedidoOrigem(pedido, actor);
        if (!canReject(actor, template, origem)) {
            throw new ConflictException("Template/origem do pedido não permite rejeição por este ator.");
        }
    }

    private boolean isRestTemplate(String templateCode) {
        return TEMPLATE_REST_V1.equals(normalizeTemplateCode(templateCode, null));
    }

    private boolean isPontoTemplate(String templateCode) {
        return TEMPLATE_PONTO_V1.equals(normalizeTemplateCode(templateCode, null));
    }

    private boolean isKdsTemplate(String templateCode) {
        return TEMPLATE_KDS.equals(normalizeTemplateCode(templateCode, null));
    }

    private boolean isCaixaTemplate(String templateCode) {
        return TEMPLATE_CAIXA.equals(normalizeTemplateCode(templateCode, null));
    }
}
