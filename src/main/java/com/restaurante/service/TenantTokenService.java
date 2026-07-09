package com.restaurante.service;

import com.restaurante.dto.request.SelectTenantRequest;
import com.restaurante.dto.response.AuthTenantOptionResponse;
import com.restaurante.dto.response.SelectTenantResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.security.JwtTokenProvider;
import com.restaurante.security.JwtPrincipal;
import com.restaurante.service.security.TenantUserAccessVersionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Emite token tenant-scoped para reduzir consultas por request no TenantResolver/Guard.
 */
@Service
@RequiredArgsConstructor
public class TenantTokenService {

    private static final Logger log = LoggerFactory.getLogger(TenantTokenService.class);

    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;
    private final com.restaurante.repository.UserRepository userRepository;
    private final com.restaurante.repository.SubscricaoRepository subscricaoRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final TenantUserAccessVersionService tenantUserAccessVersionService;
    private final PlatformTenantAccessService platformTenantAccessService;

    @Transactional(readOnly = true)
    public SelectTenantResponse selectTenant(SelectTenantRequest request) {
        if (request == null || request.getTenantId() == null) {
            throw new BusinessException("tenantId é obrigatório.");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BusinessException("Usuário não autenticado.");
        }
        User user = resolveUserFromPrincipal(auth.getPrincipal());

        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new BusinessException("Tenant não encontrado."));
        if (tenant.getEstado() != TenantEstado.ATIVO) {
            throw new BusinessException("Tenant não está ativo para operação tenant-aware.");
        }

        if (isPlatformAdmin(auth, user)) {
            String token = jwtTokenProvider.generateTenantScopedToken(
                    user,
                    tenant,
                    TenantUserRole.TENANT_ADMIN,
                    TenantUserEstado.ATIVO,
                    1,
                    null,
                    true
            );
            platformTenantAccessService.auditContextAssumed(tenant.getId());

            return SelectTenantResponse.builder()
                    .userId(user.getId())
                    .tenantId(tenant.getId())
                    .tenantCode(tenant.getTenantCode())
                    .slug(tenant.getSlug())
                    .nome(tenant.getNome())
                    .tenantNome(tenant.getNome())
                    .tokenType("Bearer")
                    .accessToken(token)
                    .expiresIn(jwtTokenProvider.getExpirationMs() / 1000L)
                    .roles(Set.of(TenantUserRole.TENANT_ADMIN.name()))
                    .build();
        }

        TenantUser membership = tenantUserRepository.findByTenantIdAndUserIdAndEstado(
                        tenant.getId(), user.getId(), TenantUserEstado.ATIVO
                )
                .orElseThrow(() -> new AccessDeniedException("Usuário não possui acesso a este tenant."));

        int accessVersion = tenantUserAccessVersionService.getAccessVersion(tenant.getId(), user.getId());
        var permissionsUpdatedAt = tenantUserAccessVersionService.getPermissionsUpdatedAt(tenant.getId(), user.getId());
        String token = jwtTokenProvider.generateTenantScopedToken(
                user,
                tenant,
                membership.getRole(),
                membership.getEstado(),
                accessVersion,
                permissionsUpdatedAt != null ? permissionsUpdatedAt.toString() : null
        );

        Set<String> roles = Set.of(membership.getRole().name());
        return SelectTenantResponse.builder()
                .userId(user.getId())
                .tenantId(tenant.getId())
                .tenantCode(tenant.getTenantCode())
                .slug(tenant.getSlug())
                .nome(tenant.getNome())
                .tenantNome(tenant.getNome())
                // tokenType aqui segue convenção HTTP Authorization ("Bearer").
                // O escopo do token (TENANT vs GLOBAL) fica no claim "tokenType" dentro do JWT.
                .tokenType("Bearer")
                .accessToken(token)
                .expiresIn(jwtTokenProvider.getExpirationMs() / 1000L)
                .roles(roles)
                .build();
    }

    /**
     * Lista todos os tenants acessíveis ao usuário autenticado via SecurityContext.
     *
     * Segurança:
     * - Exige usuário autenticado (token global JWT válido).
     * - Filtra exclusivamente por userId — nunca retorna tenants de outros usuários.
     * - Retorna apenas tenants ATIVOS com vínculo ATIVO.
     * - Retorna lista vazia se o usuário não tiver vínculos.
     * - Não expõe secrets, configurações internas ou outros usuários.
     *
     * @return lista de opções de tenant acessíveis ao usuário autenticado.
     */
    @Transactional(readOnly = true)
    public List<AuthTenantOptionResponse> listarTenantsAcessiveis() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Usuário não autenticado.");
        }
        User user = resolveUserFromPrincipal(auth.getPrincipal());
        log.info("[AUTH-TENANTS] Listando tenants acessíveis para userId={}", user.getId());

        if (isPlatformAdmin(auth, user)) {
            List<Tenant> tenants = tenantRepository.findByEstadoOrderByIdAsc(TenantEstado.ATIVO);
            log.info("[AUTH-TENANTS] Platform Admin userId={} recebeu {} tenants ativos", user.getId(), tenants.size());
            boolean first = true;
            List<AuthTenantOptionResponse> result = new java.util.ArrayList<>();
            for (Tenant t : tenants) {
                result.add(AuthTenantOptionResponse.builder()
                        .tenantId(t.getId())
                        .tenantCode(t.getTenantCode())
                        .slug(t.getSlug())
                        .nome(t.getNome())
                        .estado(t.getEstado().name())
                        .ativo(true)
                        .roles(List.of(TenantUserRole.TENANT_ADMIN.name()))
                        .templateCode(t.getTemplateCode())
                        .planoCodigo(resolvePlanoCodigo(t.getId()))
                        .principal(first)
                        .build());
                first = false;
            }
            return result;
        }

        List<TenantUser> memberships = tenantUserRepository.findActiveTenantOptionsByUserId(
                user.getId(),
                TenantUserEstado.ATIVO,
                TenantEstado.ATIVO
        );

        log.info("[AUTH-TENANTS] Encontrados {} vínculos ativos para userId={}", memberships.size(), user.getId());

        boolean first = true;
        List<AuthTenantOptionResponse> result = new java.util.ArrayList<>();
        for (TenantUser tu : memberships) {
            Tenant t = tu.getTenant();
            AuthTenantOptionResponse opt = AuthTenantOptionResponse.builder()
                    .tenantId(t.getId())
                    .tenantCode(t.getTenantCode())
                    .slug(t.getSlug())
                    .nome(t.getNome())
                    .estado(t.getEstado().name())
                    .ativo(t.getEstado() == TenantEstado.ATIVO)
                    .roles(List.of(tu.getRole().name()))
                    .templateCode(t.getTemplateCode())
                    .planoCodigo(resolvePlanoCodigo(t.getId()))
                    .principal(first)
                    .build();
            result.add(opt);
            first = false;
        }
        return result;
    }

    private User resolveUserFromPrincipal(Object principal) {
        if (principal instanceof User u) {
            return u;
        }
        if (principal instanceof JwtPrincipal jp && jp.getUserId() != null) {
            return userRepository.findById(jp.getUserId())
                    .orElseThrow(() -> new BusinessException("Usuário não encontrado."));
        }
        throw new BusinessException("Principal inválido para seleção de tenant.");
    }

    private boolean isPlatformAdmin(Authentication auth, User user) {
        if (user != null && user.isAdmin()) {
            return true;
        }
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private String resolvePlanoCodigo(Long tenantId) {
        return subscricaoRepository.findByTenantIdAndEstado(tenantId, SubscricaoEstado.ATIVA)
                .map(s -> s.getPlano() != null ? s.getPlano().getCodigo() : null)
                .orElse(null);
    }
}
