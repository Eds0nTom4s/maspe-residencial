package com.restaurante.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Plano;
import com.restaurante.model.entity.Subscricao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantLimiteOverride;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.QrCodeOperacionalRepository;
import com.restaurante.repository.SubscricaoRepository;
import com.restaurante.repository.TenantLimiteOverrideRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.DispositivoStatus;
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
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final TenantUserRepository tenantUserRepository;
    private final QrCodeOperacionalRepository qrCodeOperacionalRepository;
    private final DispositivoOperacionalRepository dispositivoOperacionalRepository;

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

    @Transactional(readOnly = true)
    public void assertCanCreateUnidadeAtendimento(Long tenantId, int quantidadeNova) {
        EffectiveTenantLimits limits = getEffectiveLimits(tenantId);
        long current = unidadeAtendimentoRepository.countByTenantId(tenantId);
        long projected = current + Math.max(0, quantidadeNova);
        if (limits.maxUnidadesAtendimento() != null && projected > limits.maxUnidadesAtendimento()) {
            throw new BusinessException("Limite de unidades de atendimento excedido para o tenant.");
        }
    }

    @Transactional(readOnly = true)
    public void assertCanCreateUser(Long tenantId, int quantidadeNova) {
        EffectiveTenantLimits limits = getEffectiveLimits(tenantId);
        // Regra (Prompt 19): contar usuários distintos com pelo menos um vínculo não-REMOVIDO.
        // SUSPENSO ainda consome limite; REMOVIDO não consome.
        long current = tenantUserRepository.countDistinctUsersByTenantIdAndEstadoNot(tenantId, TenantUserEstado.REMOVIDO);
        long projected = current + Math.max(0, quantidadeNova);
        if (limits.maxUsuarios() != null && projected > limits.maxUsuarios()) {
            throw new BusinessException("Limite de usuários excedido para o tenant.");
        }
    }

    @Transactional(readOnly = true)
    public void assertCanCreateQrCode(Long tenantId, int quantidadeNova) {
        EffectiveTenantLimits limits = getEffectiveLimits(tenantId);
        long current = qrCodeOperacionalRepository.countByTenantId(tenantId);
        long projected = current + Math.max(0, quantidadeNova);
        if (limits.maxQrCodes() != null && projected > limits.maxQrCodes()) {
            throw new BusinessException("Limite de QR Codes excedido para o tenant.");
        }
    }

    // Placeholders para fases futuras (não usados ainda)
    public void assertCanCreateProduto(Long tenantId) { /* Fase futura */ }

    @Transactional(readOnly = true)
    public void assertCanCreateDispositivo(Long tenantId, int quantidadeNova) {
        EffectiveTenantLimits limits = getEffectiveLimits(tenantId);
        long current = dispositivoOperacionalRepository.countByTenantIdAndStatusNot(tenantId, DispositivoStatus.REVOGADO);
        long projected = current + Math.max(0, quantidadeNova);
        if (limits.maxDispositivos() != null && projected > limits.maxDispositivos()) {
            throw new BusinessException("Limite de dispositivos excedido para o tenant.");
        }
    }

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
