package com.restaurante.service;

import com.restaurante.dto.response.PlatformTenantResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.Subscricao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantCardapioConfig;
import com.restaurante.model.entity.TenantLimiteOverride;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.repository.QrCodeOperacionalRepository;
import com.restaurante.repository.SubscricaoRepository;
import com.restaurante.repository.TenantCardapioConfigRepository;
import com.restaurante.repository.TenantLimiteOverrideRepository;
import com.restaurante.repository.TenantRepository;
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
    private final QrCodeOperacionalRepository qrCodeOperacionalRepository;
    private final TenantCardapioConfigRepository tenantCardapioConfigRepository;
    private final TenantLimiteOverrideRepository tenantLimiteOverrideRepository;
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
                .orElseThrow(() -> new BusinessException("Tenant não encontrado."));
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
        TenantCardapioConfig cardapioConfig = tenantCardapioConfigRepository
                .findByTenantId(tenant.getId())
                .orElse(null);
        TenantLimiteOverride limiteOverride = tenantLimiteOverrideRepository
                .findByTenantIdAndAtivoTrue(tenant.getId())
                .orElse(null);
        QrCodeOperacional qrPrincipal = qrCodeOperacionalRepository
                .findByTenantIdAndAtivoTrueAndRevogadoFalse(tenant.getId())
                .stream()
                .findFirst()
                .orElse(null);

        return PlatformTenantResponse.builder()
                .tenantId(tenant.getId())
                .nome(tenant.getNome())
                .tenantCode(tenant.getTenantCode())
                .slug(tenant.getSlug())
                .estado(tenant.getEstado() != null ? tenant.getEstado().name() : null)
                .ativo(tenant.getEstado() == TenantEstado.ATIVO)
                .templateCode(tenant.getTemplateCode())
                .templateVersion(tenant.getTemplateVersion())
                .planoCodigo(subscricao != null && subscricao.getPlano() != null ? subscricao.getPlano().getCodigo() : null)
                .criadoEm(tenant.getProvisionedAt() != null ? tenant.getProvisionedAt() : tenant.getCreatedAt())
                .qrPrincipal(toQr(qrPrincipal))
                .cardapio(toCardapio(cardapioConfig, limiteOverride))
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

    private PlatformTenantResponse.Cardapio toCardapio(TenantCardapioConfig config, TenantLimiteOverride limits) {
        return PlatformTenantResponse.Cardapio.builder()
                .publicado(config != null && Boolean.TRUE.equals(config.getCardapioPublicado()))
                .maxCategorias(limits != null ? limits.getMaxCategorias() : null)
                .maxProdutos(limits != null ? limits.getMaxProdutos() : null)
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
            // Auditoria de leitura não deve impedir a operação administrativa.
        }
    }
}
