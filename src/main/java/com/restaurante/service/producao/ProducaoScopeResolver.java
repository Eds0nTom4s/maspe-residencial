package com.restaurante.service.producao;

import com.restaurante.dto.response.MinhaUnidadeProducaoResponse;
import com.restaurante.exception.ConflictException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.UnidadeProducao;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.UnidadeProducaoRepository;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProducaoScopeResolver {

    private final TenantGuard tenantGuard;
    private final UnidadeProducaoRepository unidadeProducaoRepository;

    public boolean isDevice() {
        return getDevicePrincipal() != null;
    }

    public DevicePrincipal getDevicePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof DevicePrincipal dp) {
            return dp;
        }
        return null;
    }

    public Long requireTenantId() {
        DevicePrincipal dp = getDevicePrincipal();
        if (dp != null) return dp.tenantId();
        TenantContext ctx = tenantGuard.requireContext();
        if (ctx.tenantId() == null) throw new ResourceNotFoundException("Recurso não encontrado.");
        return ctx.tenantId();
    }

    @Transactional(readOnly = true)
    public MinhaUnidadeProducaoResponse minhaUnidade() {
        DevicePrincipal dp = getDevicePrincipal();
        if (dp != null) {
            assertDeviceCapability(dp, DeviceCapability.VIEW_PRODUCTION);
            DeviceUnitResolution resolved = resolveForDevice(dp);
            return toResponse(dp.tenantId(), resolved.unit(), resolved.mode(), null);
        }

        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_KITCHEN
        );
        TenantContext ctx = tenantGuard.requireContext();
        Long tenantId = ctx.tenantId();
        if (tenantId == null) throw new ResourceNotFoundException("Recurso não encontrado.");

        List<UnidadeProducao> active = unidadeProducaoRepository.findByTenantIdAndAtivoTrueOrderByOrdemAsc(tenantId);
        if (active.isEmpty()) {
            throw new ResourceNotFoundException("Unidade de produção não encontrada.");
        }
        if (active.size() == 1) {
            return toResponse(tenantId, active.getFirst(), MinhaUnidadeProducaoResponse.ModoResolucao.USER_SINGLE_ACTIVE, null);
        }

        // Sem vínculo explícito user->unidade nesta fase: retorna opções
        List<MinhaUnidadeProducaoResponse.Opcao> opcoes = active.stream().map(this::toOpcao).toList();
        return new MinhaUnidadeProducaoResponse(
                tenantId,
                null,
                null,
                null,
                null,
                null,
                null,
                MinhaUnidadeProducaoResponse.ModoResolucao.EXPLICIT_REQUIRED,
                opcoes
        );
    }

    @Transactional(readOnly = true)
    public DeviceUnitResolution resolveForDevice(DevicePrincipal dp) {
        // 1) Preferência: unidade produção vinculada à unidade de atendimento do device
        if (dp.unidadeAtendimentoId() != null) {
            List<UnidadeProducao> byUa = unidadeProducaoRepository.findByTenantIdAndUnidadeAtendimentoIdAndAtivoTrueOrderByOrdemAsc(
                    dp.tenantId(),
                    dp.unidadeAtendimentoId()
            );
            if (byUa.size() == 1) {
                return new DeviceUnitResolution(byUa.getFirst(), MinhaUnidadeProducaoResponse.ModoResolucao.DEVICE_UNIT);
            }
            if (byUa.size() > 1) {
                throw new ConflictException("DEVICE_PRODUCTION_UNIT_AMBIGUOUS");
            }
        }

        // 2) Fallback seguro: se não há vínculo UA->UnidadeProducao (tenants legados),
        // tenta resolver dentro da instituição do device.
        if (dp.instituicaoId() != null) {
            List<UnidadeProducao> byInstituicao = unidadeProducaoRepository.findByTenantIdAndInstituicaoIdAndAtivoTrueOrderByOrdemAsc(
                    dp.tenantId(),
                    dp.instituicaoId()
            );
            if (byInstituicao.size() == 1) {
                return new DeviceUnitResolution(byInstituicao.getFirst(), MinhaUnidadeProducaoResponse.ModoResolucao.DEVICE_INSTITUICAO_SINGLE_ACTIVE_FALLBACK);
            }
            if (byInstituicao.size() > 1) {
                // tenta rota "GERAL" quando houver múltiplas unidades na instituição
                var geral = unidadeProducaoRepository.findByTenantIdAndInstituicaoIdAndCodigo(dp.tenantId(), dp.instituicaoId(), "GERAL");
                if (geral.isPresent()) {
                    return new DeviceUnitResolution(geral.get(), MinhaUnidadeProducaoResponse.ModoResolucao.DEVICE_INSTITUICAO_SINGLE_ACTIVE_FALLBACK);
                }
                throw new ConflictException("DEVICE_PRODUCTION_UNIT_AMBIGUOUS");
            }
        }

        // 3) Fallback final: se existir apenas 1 unidade ativa no tenant, usa-a
        List<UnidadeProducao> active = unidadeProducaoRepository.findByTenantIdAndAtivoTrueOrderByOrdemAsc(dp.tenantId());
        if (active.isEmpty()) {
            throw new ResourceNotFoundException("Unidade de produção não encontrada.");
        }
        if (active.size() == 1) {
            return new DeviceUnitResolution(active.getFirst(), MinhaUnidadeProducaoResponse.ModoResolucao.DEVICE_TENANT_SINGLE_ACTIVE_FALLBACK);
        }

        throw new ConflictException("DEVICE_PRODUCTION_UNIT_AMBIGUOUS");
    }

    public void assertDeviceCapability(DevicePrincipal dp, DeviceCapability capability) {
        if (dp.capabilities() == null || !dp.capabilities().contains(capability)) {
            throw new AccessDeniedException("PRODUCTION_CAPABILITY_FORBIDDEN");
        }
    }

    public void assertNotDevice() {
        if (isDevice()) throw new AccessDeniedException("PRODUCTION_SCOPE_FORBIDDEN");
    }

    private MinhaUnidadeProducaoResponse toResponse(Long tenantId, UnidadeProducao unit, MinhaUnidadeProducaoResponse.ModoResolucao mode, List<MinhaUnidadeProducaoResponse.Opcao> opcoes) {
        return new MinhaUnidadeProducaoResponse(
                tenantId,
                unit != null ? unit.getId() : null,
                unit != null ? unit.getNome() : null,
                unit != null ? unit.getCodigo() : null,
                unit != null ? unit.getTipo() : null,
                unit != null && unit.getInstituicao() != null ? unit.getInstituicao().getId() : null,
                unit != null && unit.getUnidadeAtendimento() != null ? unit.getUnidadeAtendimento().getId() : null,
                mode,
                opcoes
        );
    }

    private MinhaUnidadeProducaoResponse.Opcao toOpcao(UnidadeProducao unit) {
        return new MinhaUnidadeProducaoResponse.Opcao(
                unit.getId(),
                unit.getNome(),
                unit.getCodigo(),
                unit.getTipo(),
                unit.getInstituicao() != null ? unit.getInstituicao().getId() : null,
                unit.getUnidadeAtendimento() != null ? unit.getUnidadeAtendimento().getId() : null
        );
    }

    public record DeviceUnitResolution(UnidadeProducao unit, MinhaUnidadeProducaoResponse.ModoResolucao mode) {}
}
