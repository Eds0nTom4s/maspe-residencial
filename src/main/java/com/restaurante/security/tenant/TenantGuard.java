package com.restaurante.security.tenant;

import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/**
 * TenantGuard: validações contextuais de tenant/membership.
 *
 * Observação (Prompt 4):
 * - Infraestrutura apenas. Não aplicado globalmente ainda.
 */
@Service
@RequiredArgsConstructor
public class TenantGuard {

    private final ObjectProvider<TenantRepository> tenantRepositoryProvider;
    private final ObjectProvider<TenantUserRepository> tenantUserRepositoryProvider;

    public TenantContext requireContext() {
        return TenantContextHolder.require();
    }

    @Transactional(readOnly = true)
    public void assertTenantActive(Long tenantId) {
        TenantRepository tenantRepository = tenantRepositoryProvider.getIfAvailable();
        if (tenantRepository == null) {
            throw new BusinessException("TenantRepository indisponível para validação.");
        }
        Tenant t = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant não encontrado: " + tenantId));
        if (t.getEstado() != TenantEstado.ATIVO) {
            throw new BusinessException("Tenant não está ativo para criação de recursos.");
        }
    }

    public void assertPlatformAdmin() {
        TenantContext ctx = requireContext();
        if (!ctx.platformAdmin()) {
            throw new BusinessException("Ação permitida apenas para PLATFORM_ADMIN.");
        }
    }

    @Transactional(readOnly = true)
    public void assertCurrentUserBelongsToTenant(Long tenantId) {
        TenantContext ctx = requireContext();
        if (ctx.userId() == null) {
            throw new BusinessException("Usuário não identificado para validação de tenant.");
        }
        if (ctx.platformAdmin()) {
            return;
        }
        TenantUserRepository tenantUserRepository = tenantUserRepositoryProvider.getIfAvailable();
        if (tenantUserRepository == null) {
            throw new BusinessException("TenantUserRepository indisponível para validação.");
        }
        boolean ok = tenantUserRepository.existsByTenantIdAndUserIdAndEstado(
                tenantId, ctx.userId(), TenantUserEstado.ATIVO
        );
        if (!ok) {
            throw new BusinessException("Usuário não pertence ao tenant.");
        }
    }

    public void assertCurrentTenant(Long tenantId) {
        TenantContext ctx = requireContext();
        if (ctx.tenantId() == null || !ctx.tenantId().equals(tenantId)) {
            throw new BusinessException("TenantContext não corresponde ao tenant requerido.");
        }
    }

    public void assertResourceBelongsToTenant(Long resourceTenantId) {
        TenantContext ctx = requireContext();
        if (ctx.platformAdmin()) {
            return;
        }
        if (ctx.tenantId() == null || !ctx.tenantId().equals(resourceTenantId)) {
            throw new BusinessException("Recurso não pertence ao tenant atual.");
        }
    }

    public void assertTenantRole(TenantUserRole role) {
        assertAnyTenantRole(java.util.List.of(role));
    }

    public void assertAnyTenantRole(Collection<TenantUserRole> roles) {
        TenantContext ctx = requireContext();
        // Por enquanto, não há roles tenant no JWT; isso será resolvido quando TenantContext estiver integrado ao Auth/JWT.
        // Mantemos o método como infra para fases futuras.
        if (ctx.platformAdmin()) {
            return;
        }
        throw new BusinessException("Validação de TenantUserRole ainda não suportada sem TenantContext/JWT tenant-aware.");
    }
}
