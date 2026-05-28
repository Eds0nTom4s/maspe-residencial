package com.restaurante.security.tenant;

import com.restaurante.exception.TenantAccessDeniedException;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Set;

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
        // Preferir contexto resolvido pelo TenantContextFilter/TenantResolver.
        // Fallback: quando não há TenantContext (ex.: user sem seleção de tenant),
        // ainda devemos responder 403 em vez de 500 para endpoints platform-scoped.
        TenantContext ctx = TenantContextHolder.get().orElse(null);
        if (ctx != null) {
            if (!ctx.platformAdmin()) {
                throw new TenantAccessDeniedException("Ação permitida apenas para PLATFORM_ADMIN.");
            }
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new TenantAccessDeniedException("Ação permitida apenas para PLATFORM_ADMIN.");
        }
        boolean isAdmin = false;
        for (GrantedAuthority a : authentication.getAuthorities()) {
            if ("ROLE_ADMIN".equals(a.getAuthority())) {
                isAdmin = true;
                break;
            }
        }
        if (!isAdmin) {
            throw new TenantAccessDeniedException("Ação permitida apenas para PLATFORM_ADMIN.");
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
        if (ctx.platformAdmin()) {
            return;
        }
        if (ctx.tenantId() == null || ctx.userId() == null) {
            throw new TenantAccessDeniedException("TenantContext obrigatório para validação de role.");
        }

        // Fast path: roles já carregadas no TenantContext (TenantResolver)
        Set<String> roleNames = ctx.roles() != null ? ctx.roles() : Set.of();
        for (TenantUserRole role : roles) {
            if (roleNames.contains(role.name())) {
                return;
            }
        }

        // Fallback: consulta ao TenantUserRepository para evitar dependência total do resolver
        TenantUserRepository tenantUserRepository = tenantUserRepositoryProvider.getIfAvailable();
        if (tenantUserRepository == null) {
            throw new BusinessException("TenantUserRepository indisponível para validação de role.");
        }
        for (TenantUserRole role : roles) {
            boolean ok = tenantUserRepository.existsByTenantIdAndUserIdAndRoleAndEstado(
                    ctx.tenantId(), ctx.userId(), role, TenantUserEstado.ATIVO
            );
            if (ok) return;
        }

        throw new TenantAccessDeniedException("Usuário não possui permissão para executar esta ação.");
    }

    public void assertAnyTenantRole(TenantUserRole... roles) {
        assertAnyTenantRole(java.util.Arrays.asList(roles));
    }

    public boolean hasAnyTenantRole(TenantUserRole... roles) {
        try {
            assertAnyTenantRole(roles);
            return true;
        } catch (AccessDeniedException ex) {
            return false;
        }
    }
}
