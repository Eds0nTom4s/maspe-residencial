package com.restaurante.service;

import com.restaurante.dto.request.SelectTenantRequest;
import com.restaurante.dto.response.SelectTenantResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.security.JwtTokenProvider;
import com.restaurante.security.JwtPrincipal;
import com.restaurante.service.security.TenantUserAccessVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Emite token tenant-scoped para reduzir consultas por request no TenantResolver/Guard.
 */
@Service
@RequiredArgsConstructor
public class TenantTokenService {

    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;
    private final com.restaurante.repository.UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final TenantUserAccessVersionService tenantUserAccessVersionService;

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
                .tenantNome(tenant.getNome())
                // tokenType aqui segue convenção HTTP Authorization ("Bearer").
                // O escopo do token (TENANT vs GLOBAL) fica no claim "tokenType" dentro do JWT.
                .tokenType("Bearer")
                .accessToken(token)
                .expiresIn(jwtTokenProvider.getExpirationMs() / 1000L)
                .roles(roles)
                .build();
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
}
