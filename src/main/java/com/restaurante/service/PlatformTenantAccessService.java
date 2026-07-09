package com.restaurante.service;

import com.restaurante.billing.repository.TenantSubscriptionRepository;
import com.restaurante.dto.response.PlatformTenantResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.BusinessAccount;
import com.restaurante.model.entity.Plano;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.Subscricao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantCardapioConfig;
import com.restaurante.model.entity.TenantLimiteOverride;
import com.restaurante.model.entity.TenantOperationalModulesConfig;
import com.restaurante.model.entity.TenantSessaoConsumoConfig;
import com.restaurante.model.entity.TenantSubscription;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.repository.QrCodeOperacionalRepository;
import com.restaurante.repository.SubscricaoRepository;
import com.restaurante.repository.TenantCardapioConfigRepository;
import com.restaurante.repository.TenantLimiteOverrideRepository;
import com.restaurante.repository.TenantOperationalModulesConfigRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantSessaoConsumoConfigRepository;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlatformTenantAccessService {

    private final TenantRepository tenantRepository;
    private final SubscricaoRepository subscricaoRepository;
    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final QrCodeOperacionalRepository qrCodeOperacionalRepository;
    private final TenantCardapioConfigRepository tenantCardapioConfigRepository;
    private final TenantLimiteOverrideRepository tenantLimiteOverrideRepository;
    private final TenantOperationalModulesConfigRepository tenantOperationalModulesConfigRepository;
    private final TenantSessaoConsumoConfigRepository tenantSessaoConsumoConfigRepository;
    private final TenantGuard tenantGuard;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public List<PlatformTenantResponse> listarTenants(Pageable pageable, String ip, String userAgent) {
        tenantGuard.assertPlatformAdmin();
        List<Tenant> tenants = tenantRepository.searchPlatform(null, null, pageable).getContent();
        if (!tenants.isEmpty()) {
            audit(
                    tenants.get(0).getId(),
                    OperationalEventType.PLATFORM_TENANT_LIST_VIEWED,
                    "Platform Admin listou tenants",
                    Map.of("origem", "PLATFORM", "resultCount", tenants.size())
            );
        }
        return tenants.stream().map(this::toResponse).toList();
    }

    @Transactional
    public PlatformTenantResponse detalhe(Long tenantId, String ip, String userAgent) {
        tenantGuard.assertPlatformAdmin();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant nao encontrado."));
        audit(
                tenant.getId(),
                OperationalEventType.PLATFORM_TENANT_DETAIL_VIEWED,
                "Platform Admin visualizou detalhe do tenant",
                Map.of("origem", "PLATFORM", "tenantId", tenant.getId())
        );
        return toResponse(tenant);
    }

    public PlatformTenantResponse toResponse(Tenant tenant) {
        Subscricao subscricao = subscricaoRepository
                .findByTenantIdAndEstado(tenant.getId(), SubscricaoEstado.ATIVA)
                .orElse(null);
        TenantSubscription tenantSubscription = tenantSubscriptionRepository
                .findTopByTenantIdOrderByIdDesc(tenant.getId())
                .orElse(null);
        TenantCardapioConfig cardapioConfig = tenantCardapioConfigRepository
                .findByTenantId(tenant.getId())
                .orElse(null);
        TenantLimiteOverride limiteOverride = tenantLimiteOverrideRepository
                .findByTenantIdAndAtivoTrue(tenant.getId())
                .orElse(null);
        TenantOperationalModulesConfig modulos = tenantOperationalModulesConfigRepository
                .findByTenantId(tenant.getId())
                .orElse(null);
        TenantSessaoConsumoConfig sessao = tenantSessaoConsumoConfigRepository
                .findByTenantId(tenant.getId())
                .orElse(null);
        QrCodeOperacional qrPrincipal = qrCodeOperacionalRepository
                .findByTenantIdAndAtivoTrueAndRevogadoFalse(tenant.getId())
                .stream()
                .findFirst()
                .orElse(null);
        Plano plano = subscricao != null ? subscricao.getPlano() : null;
        BusinessAccount businessAccount = tenant.getBusinessAccount();

        return PlatformTenantResponse.builder()
                .tenantId(tenant.getId())
                .nome(tenant.getNome())
                .tenantCode(tenant.getTenantCode())
                .slug(tenant.getSlug())
                .tipo(tenant.getTipo() != null ? tenant.getTipo().name() : null)
                .estado(tenant.getEstado() != null ? tenant.getEstado().name() : null)
                .ativo(tenant.getEstado() == TenantEstado.ATIVO)
                .templateCode(tenant.getTemplateCode())
                .templateVersion(tenant.getTemplateVersion())
                .planoCodigo(plano != null ? plano.getCodigo() : null)
                .planoNome(plano != null ? plano.getNome() : null)
                .billingPlanCode(tenantSubscription != null && tenantSubscription.getBillingPlan() != null
                        ? tenantSubscription.getBillingPlan().getCode()
                        : null)
                .billingPlanNome(tenantSubscription != null && tenantSubscription.getBillingPlan() != null
                        ? tenantSubscription.getBillingPlan().getName()
                        : null)
                .subscricaoStatus(tenantSubscription != null
                        ? tenantSubscription.getStatus().name()
                        : subscricao != null && subscricao.getEstado() != null ? subscricao.getEstado().name() : null)
                .businessAccountId(businessAccount != null ? businessAccount.getId() : null)
                .businessAccountNome(businessAccount != null ? businessAccount.getNome() : null)
                .businessAccountSlug(businessAccount != null ? businessAccount.getSlug() : null)
                .businessAccountEstado(businessAccount != null && businessAccount.getEstado() != null ? businessAccount.getEstado().name() : null)
                .criadoEm(tenant.getProvisionedAt() != null ? tenant.getProvisionedAt() : tenant.getCreatedAt())
                .qrPrincipal(toQr(qrPrincipal))
                .cardapio(toCardapio(cardapioConfig, limiteOverride, plano))
                .limites(toLimites(limiteOverride, plano))
                .modulos(toModulos(modulos))
                .sessaoConsumo(toSessao(sessao))
                .selecionavel(tenant.getEstado() == TenantEstado.ATIVO)
                .build();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void auditContextAssumed(Long tenantId) {
        audit(
                tenantId,
                OperationalEventType.PLATFORM_TENANT_CONTEXT_ASSUMED,
                "Platform Admin assumiu contexto operacional do tenant",
                Map.of("origem", "PLATFORM", "tenantId", tenantId)
        );
    }

    private PlatformTenantResponse.QrPrincipal toQr(QrCodeOperacional qr) {
        if (qr == null) return null;
        return PlatformTenantResponse.QrPrincipal.builder()
                .qrToken(qr.getToken())
                .qrUrlPublica("/q/" + qr.getToken() + "/cardapio")
                .build();
    }

    private PlatformTenantResponse.Cardapio toCardapio(TenantCardapioConfig config,
                                                       TenantLimiteOverride limits,
                                                       Plano plano) {
        return PlatformTenantResponse.Cardapio.builder()
                .publicado(config != null && Boolean.TRUE.equals(config.getCardapioPublicado()))
                .maxCategorias(limits != null && limits.getMaxCategorias() != null
                        ? limits.getMaxCategorias()
                        : plano != null ? plano.getMaxCategorias() : null)
                .maxProdutos(limits != null && limits.getMaxProdutos() != null
                        ? limits.getMaxProdutos()
                        : plano != null ? plano.getMaxProdutos() : null)
                .build();
    }

    private PlatformTenantResponse.Limites toLimites(TenantLimiteOverride limits, Plano plano) {
        return PlatformTenantResponse.Limites.builder()
                .maxInstituicoes(plano != null ? plano.getMaxInstituicoes() : null)
                .maxUnidadesAtendimento(plano != null ? plano.getMaxUnidadesAtendimento() : null)
                .maxProdutos(limits != null && limits.getMaxProdutos() != null
                        ? limits.getMaxProdutos()
                        : plano != null ? plano.getMaxProdutos() : null)
                .maxCategorias(limits != null && limits.getMaxCategorias() != null
                        ? limits.getMaxCategorias()
                        : plano != null ? plano.getMaxCategorias() : null)
                .maxUsuarios(limits != null ? limits.getMaxUsuarios() : plano != null ? plano.getMaxUsuarios() : null)
                .maxQrCodes(limits != null ? limits.getMaxQrCodes() : plano != null ? plano.getMaxQrCodes() : null)
                .maxDispositivos(limits != null ? limits.getMaxDispositivos() : plano != null ? plano.getMaxDispositivos() : null)
                .build();
    }

    private PlatformTenantResponse.Modulos toModulos(TenantOperationalModulesConfig config) {
        if (config == null) {
            return null;
        }
        return PlatformTenantResponse.Modulos.builder()
                .sessaoConsumoEnabled(config.isSessaoConsumoEnabled())
                .pedidoDiretoEnabled(config.isPedidoDiretoEnabled())
                .mesasEnabled(config.isMesasEnabled())
                .qrMesaEnabled(config.isQrMesaEnabled())
                .caixaEnabled(config.isCaixaEnabled())
                .kdsEnabled(config.isKdsEnabled())
                .build();
    }

    private PlatformTenantResponse.SessaoConsumo toSessao(TenantSessaoConsumoConfig config) {
        if (config == null) {
            return null;
        }
        return PlatformTenantResponse.SessaoConsumo.builder()
                .enabled(config.isEnabled())
                .permitirPrePago(config.isPermitirPrePago())
                .permitirPosPago(config.isPermitirPosPago())
                .permitirModoAnonimo(config.isPermitirModoAnonimo())
                .expiracaoHoras(config.getExpiracaoHoras())
                .build();
    }

    private void audit(Long tenantId, OperationalEventType eventType, String motivo, Map<String, Object> metadata) {
        try {
            operationalEventLogService.logGenericForTenant(
                    tenantId,
                    eventType,
                    OperationalEntityType.TENANT,
                    tenantId,
                    OperationalOrigem.SYSTEM,
                    motivo,
                    metadata,
                    null,
                    null
            );
        } catch (RuntimeException ignored) {
            // Auditoria de leitura nao deve impedir a operacao administrativa.
        }
    }
}
