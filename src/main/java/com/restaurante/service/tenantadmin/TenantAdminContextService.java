package com.restaurante.service.tenantadmin;

import com.restaurante.dto.response.TenantMeResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Plano;
import com.restaurante.model.entity.Subscricao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.SubscricaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.TenantLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantAdminContextService {

    private final TenantGuard tenantGuard;
    private final TenantRepository tenantRepository;
    private final SubscricaoRepository subscricaoRepository;
    private final InstituicaoRepository instituicaoRepository;
    private final UserRepository userRepository;
    private final TenantLimitService tenantLimitService;

    @Transactional(readOnly = true)
    public TenantMeResponse me() {
        TenantContext ctx = tenantGuard.requireContext();
        Long tenantId = ctx.tenantId();
        if (tenantId == null) {
            throw new BusinessException("TenantContext ausente para endpoint tenant.");
        }

        tenantGuard.assertCurrentUserBelongsToTenant(tenantId);
        tenantGuard.assertTenantActive(tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant não encontrado: " + tenantId));

        Subscricao subscricaoAtiva = subscricaoRepository.findByTenantIdAndEstado(tenantId, SubscricaoEstado.ATIVA)
                .orElse(null);
        Plano plano = subscricaoAtiva != null ? subscricaoAtiva.getPlano() : null;

        TenantLimitService.EffectiveTenantLimits effectiveLimits = tenantLimitService.getEffectiveLimits(tenantId);

        User user = ctx.userId() != null ? userRepository.findById(ctx.userId()).orElse(null) : null;

        List<TenantMeResponse.InstituicaoResumoResponse> instituicoes = instituicaoRepository.findByTenantId(tenantId).stream()
                .map(this::toInstituicaoResumo)
                .toList();

        return TenantMeResponse.builder()
                .tenantId(tenant.getId())
                .nome(tenant.getNome())
                .slug(tenant.getSlug())
                .tenantCode(tenant.getTenantCode())
                .estado(tenant.getEstado())
                .roles(ctx.roles())
                .planoCodigo(plano != null ? plano.getCodigo() : null)
                .planoNome(plano != null ? plano.getNome() : null)
                .limites(TenantMeResponse.EffectiveLimitsResponse.builder()
                        .maxInstituicoes(effectiveLimits.maxInstituicoes())
                        .maxUnidadesAtendimento(effectiveLimits.maxUnidadesAtendimento())
                        .maxProdutos(effectiveLimits.maxProdutos())
                        .maxUsuarios(effectiveLimits.maxUsuarios())
                        .maxQrCodes(effectiveLimits.maxQrCodes())
                        .maxDispositivos(effectiveLimits.maxDispositivos())
                        .build())
                .usuario(TenantMeResponse.UserMeResponse.builder()
                        .userId(user != null ? user.getId() : ctx.userId())
                        .username(user != null ? user.getUsername() : null)
                        .nomeCompleto(user != null ? user.getNomeCompleto() : null)
                        .email(user != null ? user.getEmail() : null)
                        .telefone(user != null ? user.getTelefone() : null)
                        .build())
                .instituicoes(instituicoes)
                .build();
    }

    private TenantMeResponse.InstituicaoResumoResponse toInstituicaoResumo(Instituicao i) {
        return TenantMeResponse.InstituicaoResumoResponse.builder()
                .id(i.getId())
                .nome(i.getNome())
                .sigla(i.getSigla())
                .ativa(Boolean.TRUE.equals(i.getAtiva()))
                .build();
    }
}

