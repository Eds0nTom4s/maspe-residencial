package com.restaurante.service;

import com.restaurante.dto.request.TenantSessaoConsumoConfigRequest;
import com.restaurante.dto.response.TenantSessaoConsumoConfigResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantSessaoConsumoConfig;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TipoSessao;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantSessaoConsumoConfigRepository;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantSessaoConsumoConfigService {

    private final TenantGuard tenantGuard;
    private final TenantRepository tenantRepository;
    private final TenantSessaoConsumoConfigRepository repository;
    private final OperationalEventLogService operationalEventLogService;
    private final ObjectProvider<TenantOperationalModulesService> modulesServiceProvider;

    @Transactional(readOnly = true)
    public TenantSessaoConsumoConfigResponse obterDoTenantAtual() {
        Long tenantId = requireTenantId("leitura da configuração de sessão de consumo");
        return toResponse(obterParaTenant(tenantId));
    }

    @Transactional
    public TenantSessaoConsumoConfigResponse atualizarDoTenantAtual(TenantSessaoConsumoConfigRequest request) {
        Long tenantId = requireTenantId("atualização da configuração de sessão de consumo");
        if (!modulesServiceProvider.getObject().obterParaTenant(tenantId).isSessaoConsumoEnabled()) {
            throw new BusinessException("Sessão de consumo está desativada pela Platform para este tenant.");
        }
        TenantSessaoConsumoConfig config = getOrCreate(tenantId);
        apply(config, request);
        config.setUpdatedByUserId(TenantContextHolder.require().userId());
        TenantSessaoConsumoConfig saved = repository.saveAndFlush(config);
        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.SESSAO_CONSUMO_POLICY_UPDATED,
                OperationalEntityType.TENANT_SESSAO_CONSUMO_CONFIG,
                saved.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Política de sessão de consumo atualizada",
                metadata(saved),
                null,
                null
        );
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TenantSessaoConsumoConfig obterParaTenant(Long tenantId) {
        return repository.findByTenantId(tenantId).orElseGet(() -> defaultForRead(tenantId));
    }

    @Transactional
    public TenantSessaoConsumoConfig getOrCreate(Long tenantId) {
        return repository.findByTenantId(tenantId).orElseGet(() -> {
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new BusinessException("Tenant não encontrado: " + tenantId));
            TenantSessaoConsumoConfig config = defaultForTenant(tenant);
            config.setTenant(tenant);
            config.setEnabled(modulesServiceProvider.getObject().obterParaTenant(tenantId).isSessaoConsumoEnabled());
            return repository.saveAndFlush(config);
        });
    }

    @Transactional
    public void sincronizarModuloSessao(Long tenantId, boolean enabled) {
        TenantSessaoConsumoConfig config = getOrCreate(tenantId);
        config.setEnabled(enabled);
        repository.saveAndFlush(config);
    }

    @Transactional
    public TenantSessaoConsumoConfig upsertForTemplate(Tenant tenant,
                                                       boolean enabled,
                                                       boolean permitirPrePago,
                                                       boolean permitirPosPago,
                                                       TipoSessao tipoSessaoPadrao,
                                                       boolean exigirSaldoParaPedido,
                                                       boolean permitirModoAnonimo,
                                                       boolean permitirSessaoSemMesa,
                                                       boolean permitirSessaoComMesa,
                                                       Integer expiracaoHoras) {
        TenantSessaoConsumoConfig config = repository.findByTenantId(tenant.getId()).orElseGet(() -> {
            TenantSessaoConsumoConfig n = new TenantSessaoConsumoConfig();
            n.setTenant(tenant);
            return n;
        });
        config.setEnabled(enabled);
        config.setPermitirPrePago(permitirPrePago);
        config.setPermitirPosPago(permitirPosPago);
        config.setTipoSessaoPadrao(tipoSessaoPadrao != null ? tipoSessaoPadrao : TipoSessao.POS_PAGO);
        config.setExigirSaldoParaPedido(exigirSaldoParaPedido);
        config.setPermitirModoAnonimo(permitirModoAnonimo);
        config.setPermitirSessaoSemMesa(permitirSessaoSemMesa);
        config.setPermitirSessaoComMesa(permitirSessaoComMesa);
        config.setExpiracaoHoras(expiracaoHoras != null ? expiracaoHoras : 12);
        return repository.saveAndFlush(config);
    }

    public void assertPosPagoPermitido(Long tenantId) {
        TenantSessaoConsumoConfig config = obterParaTenant(tenantId);
        if (!config.isEnabled() || !config.isPermitirPosPago()) {
            throw new BusinessException("Pós-pago depende do módulo e da política de sessão de consumo ativos.");
        }
    }

    private void apply(TenantSessaoConsumoConfig config, TenantSessaoConsumoConfigRequest request) {
        boolean permitirPrePago = Boolean.TRUE.equals(request.getPermitirPrePago());
        boolean permitirPosPago = Boolean.TRUE.equals(request.getPermitirPosPago());
        if (!permitirPrePago && !permitirPosPago) {
            throw new BusinessException("A configuração deve permitir ao menos pré-pago ou pós-pago.");
        }
        if (request.getTipoSessaoPadrao() == TipoSessao.PRE_PAGO && !permitirPrePago) {
            throw new BusinessException("Tipo de sessão padrão pré-pago exige permitir pré-pago.");
        }
        if (request.getTipoSessaoPadrao() == TipoSessao.POS_PAGO && !permitirPosPago) {
            throw new BusinessException("Tipo de sessão padrão pós-pago exige permitir pós-pago.");
        }
        config.setPermitirPrePago(permitirPrePago);
        config.setPermitirPosPago(permitirPosPago);
        config.setTipoSessaoPadrao(request.getTipoSessaoPadrao());
        config.setExigirSaldoParaPedido(Boolean.TRUE.equals(request.getExigirSaldoParaPedido()));
        config.setPermitirModoAnonimo(Boolean.TRUE.equals(request.getPermitirModoAnonimo()));
        config.setPermitirSessaoSemMesa(Boolean.TRUE.equals(request.getPermitirSessaoSemMesa()));
        config.setPermitirSessaoComMesa(Boolean.TRUE.equals(request.getPermitirSessaoComMesa()));
        config.setExpiracaoHoras(request.getExpiracaoHoras());
    }

    private TenantSessaoConsumoConfig defaultForRead(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant não encontrado: " + tenantId));
        TenantSessaoConsumoConfig config = defaultForTenant(tenant);
        config.setTenant(tenant);
        config.setEnabled(modulesServiceProvider.getObject().obterParaTenant(tenantId).isSessaoConsumoEnabled());
        return config;
    }

    private TenantSessaoConsumoConfig defaultForTenant(Tenant tenant) {
        TenantSessaoConsumoConfig config = new TenantSessaoConsumoConfig();
        boolean ponto = tenant != null && "CONSUMA_PONTO".equalsIgnoreCase(tenant.getTemplateCode());
        config.setEnabled(!ponto);
        config.setPermitirPrePago(!ponto);
        config.setPermitirPosPago(!ponto);
        config.setTipoSessaoPadrao(ponto ? TipoSessao.PRE_PAGO : TipoSessao.POS_PAGO);
        config.setExigirSaldoParaPedido(ponto);
        config.setPermitirModoAnonimo(true);
        config.setPermitirSessaoSemMesa(true);
        config.setPermitirSessaoComMesa(!ponto);
        config.setExpiracaoHoras(12);
        return config;
    }

    public TenantSessaoConsumoConfigResponse toResponse(TenantSessaoConsumoConfig config) {
        return TenantSessaoConsumoConfigResponse.builder()
                .tenantId(config.getTenant() != null ? config.getTenant().getId() : null)
                .enabled(config.isEnabled())
                .permitirPrePago(config.isPermitirPrePago())
                .permitirPosPago(config.isPermitirPosPago())
                .tipoSessaoPadrao(config.getTipoSessaoPadrao())
                .exigirSaldoParaPedido(config.isExigirSaldoParaPedido())
                .permitirModoAnonimo(config.isPermitirModoAnonimo())
                .permitirSessaoSemMesa(config.isPermitirSessaoSemMesa())
                .permitirSessaoComMesa(config.isPermitirSessaoComMesa())
                .expiracaoHoras(config.getExpiracaoHoras())
                .updatedByUserId(config.getUpdatedByUserId())
                .atualizadoEm(config.getUpdatedAt() != null ? config.getUpdatedAt() : config.getCreatedAt())
                .build();
    }

    private Long requireTenantId(String operacao) {
        Long tenantId = TenantContextHolder.require().tenantId();
        if (tenantId == null) {
            throw new BusinessException("TenantContext ausente para " + operacao + ".");
        }
        tenantGuard.assertTenantActive(tenantId);
        tenantGuard.assertCurrentUserBelongsToTenant(tenantId);
        return tenantId;
    }

    private Map<String, Object> metadata(TenantSessaoConsumoConfig config) {
        return Map.of(
                "enabled", config.isEnabled(),
                "permitirPrePago", config.isPermitirPrePago(),
                "permitirPosPago", config.isPermitirPosPago(),
                "tipoSessaoPadrao", config.getTipoSessaoPadrao().name(),
                "exigirSaldoParaPedido", config.isExigirSaldoParaPedido()
        );
    }
}
