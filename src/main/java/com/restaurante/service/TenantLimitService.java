package com.restaurante.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Plano;
import com.restaurante.model.entity.Subscricao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantLimiteOverride;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.SubscricaoRepository;
import com.restaurante.repository.TenantLimiteOverrideRepository;
import com.restaurante.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TenantLimitService
 *
 * Responsabilidades (Prompt 3):
 * - Calcular limites efetivos de um Tenant (Plano + Override)
 * - Validar criação de recursos (inicialmente: Instituicao)
 *
 * Observação:
 * - Não implementa TenantContext/TenantGuard ainda.
 * - Não faz enforcement global; o enforcement ocorre nos pontos que chamarem este service.
 */
@Service
@RequiredArgsConstructor
public class TenantLimitService {

    private final TenantRepository tenantRepository;
    private final SubscricaoRepository subscricaoRepository;
    private final TenantLimiteOverrideRepository tenantLimiteOverrideRepository;
    private final InstituicaoRepository instituicaoRepository;

    @Transactional(readOnly = true)
    public EffectiveTenantLimits getEffectiveLimits(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant não encontrado: " + tenantId));

        if (tenant.getEstado() != TenantEstado.ATIVO) {
            throw new BusinessException("Tenant não está ativo para criação de recursos.");
        }

        Subscricao subscricaoAtiva = subscricaoRepository.findByTenantIdAndEstado(tenantId, SubscricaoEstado.ATIVA)
                .orElseThrow(() -> new BusinessException("Tenant não possui subscrição ativa."));

        Plano plano = subscricaoAtiva.getPlano();
        TenantLimiteOverride override = tenantLimiteOverrideRepository.findByTenantIdAndAtivoTrue(tenantId).orElse(null);

        Integer maxInstituicoes = pickOverrideOrPlano(
                override != null ? override.getMaxInstituicoes() : null,
                plano.getMaxInstituicoes()
        );
        Integer maxUnidadesAtendimento = pickOverrideOrPlano(
                override != null ? override.getMaxUnidadesAtendimento() : null,
                plano.getMaxUnidadesAtendimento()
        );
        Integer maxProdutos = pickOverrideOrPlano(
                override != null ? override.getMaxProdutos() : null,
                plano.getMaxProdutos()
        );
        Integer maxUsuarios = pickOverrideOrPlano(
                override != null ? override.getMaxUsuarios() : null,
                plano.getMaxUsuarios()
        );
        Integer maxQrCodes = pickOverrideOrPlano(
                override != null ? override.getMaxQrCodes() : null,
                plano.getMaxQrCodes()
        );
        Integer maxDispositivos = pickOverrideOrPlano(
                override != null ? override.getMaxDispositivos() : null,
                plano.getMaxDispositivos()
        );

        return new EffectiveTenantLimits(
                tenantId,
                maxInstituicoes,
                maxUnidadesAtendimento,
                maxProdutos,
                maxUsuarios,
                maxQrCodes,
                maxDispositivos
        );
    }

    @Transactional(readOnly = true)
    public void assertCanCreateInstituicao(Long tenantId) {
        EffectiveTenantLimits limits = getEffectiveLimits(tenantId);
        long current = instituicaoRepository.countByTenantId(tenantId);
        if (limits.maxInstituicoes() != null && current >= limits.maxInstituicoes()) {
            throw new BusinessException("Limite de instituições excedido para o tenant.");
        }
    }

    // Placeholders para fases futuras (não usados ainda)
    public void assertCanCreateProduto(Long tenantId) { /* Fase futura */ }
    public void assertCanCreateUser(Long tenantId) { /* Fase futura */ }
    public void assertCanCreateQrCode(Long tenantId) { /* Fase futura */ }
    public void assertCanCreateDispositivo(Long tenantId) { /* Fase futura */ }

    private Integer pickOverrideOrPlano(Integer overrideValue, Integer planoValue) {
        return overrideValue != null ? overrideValue : planoValue;
    }

    public record EffectiveTenantLimits(
            Long tenantId,
            Integer maxInstituicoes,
            Integer maxUnidadesAtendimento,
            Integer maxProdutos,
            Integer maxUsuarios,
            Integer maxQrCodes,
            Integer maxDispositivos
    ) {
    }
}

