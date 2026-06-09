package com.restaurante.service;

import com.restaurante.dto.request.PlatformTenantOperationalModulesPatchRequest;
import com.restaurante.dto.response.TenantOperationalModulesResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantOperationalModulesConfig;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.TenantOperationalModulesConfigRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantOperationalModulesService {

    private final TenantGuard tenantGuard;
    private final TenantRepository tenantRepository;
    private final TenantOperationalModulesConfigRepository repository;
    private final OperationalEventLogService operationalEventLogService;
    private final ObjectProvider<TenantSessaoConsumoConfigService> sessaoConsumoConfigServiceProvider;

    @Transactional(readOnly = true)
    public TenantOperationalModulesResponse obterDoTenantAtual() {
        Long tenantId = TenantContextHolder.require().tenantId();
        if (tenantId == null) {
            throw new BusinessException("TenantContext ausente para leitura de módulos operacionais.");
        }
        tenantGuard.assertCurrentUserBelongsToTenant(tenantId);
        return toResponse(obterParaTenant(tenantId));
    }

    @Transactional(readOnly = true)
    public TenantOperationalModulesConfig obterParaTenant(Long tenantId) {
        return repository.findByTenantId(tenantId).orElseGet(() -> defaultForRead(tenantId));
    }

    @Transactional
    public TenantOperationalModulesConfig getOrCreate(Long tenantId) {
        return repository.findByTenantId(tenantId).orElseGet(() -> {
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new BusinessException("Tenant não encontrado: " + tenantId));
            TenantOperationalModulesConfig config = defaultForTenant(tenant);
            config.setTenant(tenant);
            return repository.saveAndFlush(config);
        });
    }

    @Transactional
    public TenantOperationalModulesResponse atualizarPelaPlatform(Long tenantId, PlatformTenantOperationalModulesPatchRequest request) {
        tenantGuard.assertPlatformAdmin();
        TenantOperationalModulesConfig config = getOrCreate(tenantId);
        if (request.getSessaoConsumoEnabled() != null) {
            config.setSessaoConsumoEnabled(request.getSessaoConsumoEnabled());
        }
        if (request.getPedidoDiretoEnabled() != null) {
            config.setPedidoDiretoEnabled(request.getPedidoDiretoEnabled());
        }
        if (request.getMesasEnabled() != null) {
            config.setMesasEnabled(request.getMesasEnabled());
        }
        if (request.getQrMesaEnabled() != null) {
            config.setQrMesaEnabled(request.getQrMesaEnabled());
        }
        if (request.getCaixaEnabled() != null) {
            config.setCaixaEnabled(request.getCaixaEnabled());
        }
        if (request.getKdsEnabled() != null) {
            config.setKdsEnabled(request.getKdsEnabled());
        }

        Long userId = TenantContextHolder.get().map(ctx -> ctx.userId()).orElse(null);
        config.setConfiguredByPlatformUserId(userId);
        config.setConfiguredAt(LocalDateTime.now());
        TenantOperationalModulesConfig saved = repository.saveAndFlush(config);
        sessaoConsumoConfigServiceProvider.getObject().sincronizarModuloSessao(tenantId, saved.isSessaoConsumoEnabled());

        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.OPERATIONAL_MODULES_UPDATED,
                OperationalEntityType.TENANT_OPERATIONAL_MODULES,
                saved.getId(),
                OperationalOrigem.SYSTEM,
                request.getMotivo() != null && !request.getMotivo().isBlank()
                        ? request.getMotivo().trim()
                        : "Módulos operacionais atualizados pela Platform",
                metadata(saved),
                null,
                null
        );
        return toResponse(saved);
    }

    @Transactional
    public TenantOperationalModulesConfig upsertForTemplate(Tenant tenant,
                                                            boolean sessaoConsumoEnabled,
                                                            boolean pedidoDiretoEnabled,
                                                            boolean mesasEnabled,
                                                            boolean qrMesaEnabled,
                                                            boolean caixaEnabled,
                                                            boolean kdsEnabled) {
        TenantOperationalModulesConfig config = repository.findByTenantId(tenant.getId()).orElseGet(() -> {
            TenantOperationalModulesConfig n = new TenantOperationalModulesConfig();
            n.setTenant(tenant);
            return n;
        });
        config.setSessaoConsumoEnabled(sessaoConsumoEnabled);
        config.setPedidoDiretoEnabled(pedidoDiretoEnabled);
        config.setMesasEnabled(mesasEnabled);
        config.setQrMesaEnabled(qrMesaEnabled);
        config.setCaixaEnabled(caixaEnabled);
        config.setKdsEnabled(kdsEnabled);
        config.setConfiguredAt(LocalDateTime.now());
        return repository.saveAndFlush(config);
    }

    public void assertMesasEnabled(Long tenantId) {
        if (!obterParaTenant(tenantId).isMesasEnabled()) {
            throw new BusinessException("O módulo de mesas está desativado para este tenant.");
        }
    }

    public void assertQrMesaEnabled(Long tenantId) {
        TenantOperationalModulesConfig modules = obterParaTenant(tenantId);
        if (!modules.isQrMesaEnabled()) {
            throw new BusinessException("O módulo de QR por mesa está desativado para este tenant.");
        }
    }

    public void assertPedidoDiretoEnabled(Long tenantId) {
        if (!obterParaTenant(tenantId).isPedidoDiretoEnabled()) {
            throw new BusinessException("Pedido direto está desativado para este tenant.");
        }
    }

    private TenantOperationalModulesConfig defaultForRead(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant não encontrado: " + tenantId));
        TenantOperationalModulesConfig config = defaultForTenant(tenant);
        config.setTenant(tenant);
        return config;
    }

    private TenantOperationalModulesConfig defaultForTenant(Tenant tenant) {
        TenantOperationalModulesConfig config = new TenantOperationalModulesConfig();
        boolean ponto = isPonto(tenant);
        config.setSessaoConsumoEnabled(!ponto);
        config.setPedidoDiretoEnabled(true);
        config.setMesasEnabled(!ponto);
        config.setQrMesaEnabled(!ponto);
        config.setCaixaEnabled(true);
        config.setKdsEnabled(!ponto);
        return config;
    }

    private boolean isPonto(Tenant tenant) {
        if (tenant == null) {
            return false;
        }
        if ("CONSUMA_PONTO".equalsIgnoreCase(tenant.getTemplateCode())) {
            return true;
        }
        return tenant.getTipo() == TenantTipo.VENDEDOR_RUA || tenant.getTipo() == TenantTipo.LOJA;
    }

    public TenantOperationalModulesResponse toResponse(TenantOperationalModulesConfig config) {
        return TenantOperationalModulesResponse.builder()
                .tenantId(config.getTenant() != null ? config.getTenant().getId() : null)
                .sessaoConsumoEnabled(config.isSessaoConsumoEnabled())
                .pedidoDiretoEnabled(config.isPedidoDiretoEnabled())
                .mesasEnabled(config.isMesasEnabled())
                .qrMesaEnabled(config.isQrMesaEnabled())
                .caixaEnabled(config.isCaixaEnabled())
                .kdsEnabled(config.isKdsEnabled())
                .configuredByPlatformUserId(config.getConfiguredByPlatformUserId())
                .configuredAt(config.getConfiguredAt())
                .build();
    }

    private Map<String, Object> metadata(TenantOperationalModulesConfig config) {
        return Map.of(
                "sessaoConsumoEnabled", config.isSessaoConsumoEnabled(),
                "pedidoDiretoEnabled", config.isPedidoDiretoEnabled(),
                "mesasEnabled", config.isMesasEnabled(),
                "qrMesaEnabled", config.isQrMesaEnabled(),
                "caixaEnabled", config.isCaixaEnabled(),
                "kdsEnabled", config.isKdsEnabled()
        );
    }
}
